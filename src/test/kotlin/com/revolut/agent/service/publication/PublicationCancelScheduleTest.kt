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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

class PublicationCancelScheduleTest {

	private lateinit var repository: PublicationRepository
	private lateinit var orchestrator: PublicationOrchestratorService
	private val stored = AtomicReference<Publication>()

	@BeforeEach
	fun setUp() {
		repository = mock(PublicationRepository::class.java)
		val ai = mock(AiGenerationService::class.java)
		val linkedIn = mock(LinkedInPublishingService::class.java)
		orchestrator = PublicationOrchestratorService(repository, ai, linkedIn)

		val pub = awaitingReviewPub()
		stored.set(pub)

		`when`(repository.findById("pub-1")).thenAnswer { Mono.just(stored.get()) }
		`when`(repository.existsByStatus(PublicationStatus.IN_PROGRESS)).thenReturn(Mono.just(false))
		`when`(repository.save(any(Publication::class.java))).thenAnswer { inv ->
			val p = inv.getArgument<Publication>(0)
			stored.set(p)
			Mono.just(p)
		}
	}

	@Test
	fun `cancel from AWAITING_REVIEW frees lock`() {
		StepVerifier.create(orchestrator.cancelPublication("pub-1"))
			.assertNext { pub ->
				assertEquals(PublicationStatus.CANCELLED, pub.status)
				assertNull(pub.scheduledPublishAt)
			}
			.verifyComplete()
	}

	@Test
	fun `cancel from SUCCESS fails`() {
		stored.set(awaitingReviewPub().copy(status = PublicationStatus.SUCCESS))
		StepVerifier.create(orchestrator.cancelPublication("pub-1"))
			.expectError(PublicationInvalidStateException::class.java)
			.verify()
	}

	@Test
	fun `schedule from AWAITING_REVIEW sets SCHEDULED`() {
		val publishAt = Instant.now().plus(2, ChronoUnit.HOURS)
		StepVerifier.create(orchestrator.schedulePublication("pub-1", "texte planifié", publishAt))
			.assertNext { pub ->
				assertEquals(PublicationStatus.SCHEDULED, pub.status)
				assertEquals(publishAt, pub.scheduledPublishAt)
				assertEquals(
					"texte planifié",
					pub.steps.first { it.stepName == StepName.CONTENT_GENERATION }.output,
				)
			}
			.verifyComplete()
	}

	@Test
	fun `schedule rejects past publishAt`() {
		val past = Instant.now().minusSeconds(60)
		StepVerifier.create(orchestrator.schedulePublication("pub-1", "texte", past))
			.expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("future") }
			.verify()
	}

	@Test
	fun `cancel from SCHEDULED works`() {
		stored.set(
			awaitingReviewPub().copy(
				status = PublicationStatus.SCHEDULED,
				scheduledPublishAt = Instant.now().plusSeconds(3600),
			),
		)
		StepVerifier.create(orchestrator.cancelPublication("pub-1"))
			.assertNext { assertEquals(PublicationStatus.CANCELLED, it.status) }
			.verifyComplete()
	}

	private fun awaitingReviewPub() = Publication(
		id = "pub-1",
		triggerType = PublicationTriggerType.MANUAL,
		status = PublicationStatus.AWAITING_REVIEW,
		triggeredAt = Instant.parse("2026-07-18T12:00:00Z"),
		steps = listOf(
			PublicationStepResult(
				stepName = StepName.CONTENT_GENERATION,
				status = StepStatus.SUCCESS,
				output = "draft",
			),
			PublicationStepResult(
				stepName = StepName.LINKEDIN_PUBLISH,
				status = StepStatus.PENDING,
			),
		),
		llmProvider = LlmProvider.GEMINI,
	)
}
