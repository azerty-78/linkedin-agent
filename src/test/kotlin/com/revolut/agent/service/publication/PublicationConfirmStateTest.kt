package com.revolut.agent.service.publication

import com.revolut.agent.exception.publication.PublicationInvalidStateException
import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationStepResult
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.model.publication.StepName
import com.revolut.agent.model.publication.StepStatus
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.repository.publication.PublicationRepository
import com.revolut.agent.service.ai.AiGenerationService
import com.revolut.agent.service.linkedin.LinkedInPublishingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Vérifie qu’un confirm hors AWAITING_REVIEW → 409 métier.
 */
class PublicationConfirmStateTest {

	private lateinit var repository: PublicationRepository
	private lateinit var orchestrator: PublicationOrchestratorService
	private val stored = AtomicReference<Publication>()

	@BeforeEach
	fun setUp() {
		repository = mock(PublicationRepository::class.java)
		val ai = mock(AiGenerationService::class.java)
		val linkedIn = mock(LinkedInPublishingService::class.java)
		orchestrator = PublicationOrchestratorService(repository, ai, linkedIn)

		val pub = Publication(
			id = "pub-1",
			triggerType = PublicationTriggerType.MANUAL,
			status = PublicationStatus.SUCCESS,
			triggeredAt = Instant.parse("2026-07-18T12:00:00Z"),
			steps = listOf(
				PublicationStepResult(
					stepName = StepName.CONTENT_GENERATION,
					status = StepStatus.SUCCESS,
					output = "draft",
				),
			),
			llmProvider = LlmProvider.GEMINI,
		)
		stored.set(pub)

		`when`(repository.findById("pub-1")).thenReturn(Mono.just(stored.get()))
		`when`(repository.existsByStatus(PublicationStatus.IN_PROGRESS)).thenReturn(Mono.just(false))
		`when`(repository.save(any(Publication::class.java))).thenAnswer { inv ->
			val p = inv.getArgument<Publication>(0)
			stored.set(p)
			Mono.just(p)
		}
	}

	@Test
	fun `confirm on SUCCESS status fails with invalid state`() {
		StepVerifier.create(orchestrator.confirmPublication("pub-1", "texte final ok"))
			.expectError(PublicationInvalidStateException::class.java)
			.verify()
	}

	@Test
	fun `confirm rejects blank content`() {
		StepVerifier.create(orchestrator.confirmPublication("pub-1", "   "))
			.expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("blank") }
			.verify()
	}

	@Test
	fun `confirm rejects content longer than 3000`() {
		val tooLong = "a".repeat(3001)
		StepVerifier.create(orchestrator.confirmPublication("pub-1", tooLong))
			.expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("3000") }
			.verify()
	}

	@Test
	fun `awaiting review status value exists`() {
		assertEquals("AWAITING_REVIEW", PublicationStatus.AWAITING_REVIEW.name)
	}

	@Test
	fun `cancelled and scheduled status values exist`() {
		assertEquals("CANCELLED", PublicationStatus.CANCELLED.name)
		assertEquals("SCHEDULED", PublicationStatus.SCHEDULED.name)
	}
}
