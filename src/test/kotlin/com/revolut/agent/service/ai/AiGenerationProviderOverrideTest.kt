package com.revolut.agent.service.ai

import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.exception.ai.AiGenerationException
import com.revolut.agent.model.settings.BrandTone
import com.revolut.agent.model.settings.ContentLanguage
import com.revolut.agent.model.settings.EmojiPolicy
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.PostLengthPreference
import com.revolut.agent.service.settings.OrganizationSettingsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * Couvre la résolution provider (override vs active) + 422 clé manquante.
 */
class AiGenerationProviderOverrideTest {

	private lateinit var settingsService: OrganizationSettingsService
	private lateinit var promptContextBuilder: PromptContextBuilder
	private lateinit var contentPostProcessor: ContentPostProcessor
	private lateinit var service: AiGenerationService

	@BeforeEach
	fun setUp() {
		settingsService = mock(OrganizationSettingsService::class.java)
		promptContextBuilder = PromptContextBuilder()
		contentPostProcessor = ContentPostProcessor()
		service = AiGenerationService(
			organizationSettingsService = settingsService,
			promptContextBuilder = promptContextBuilder,
			contentPostProcessor = contentPostProcessor,
			aiModelProperties = com.revolut.agent.config.AiModelProperties(),
			generators = emptyList(),
		)
	}

	@Test
	fun `requireProviderReady uses override and fails with FR message when key missing`() {
		`when`(settingsService.getSettings()).thenReturn(
			Mono.just(settingsWithKeys(geminiMasked = "", active = LlmProvider.OPENAI)),
		)

		StepVerifier.create(service.requireProviderReady(LlmProvider.GEMINI))
			.expectErrorMatches { ex ->
				ex is AiGenerationException &&
					ex.message!!.contains("Clé API GEMINI manquante") &&
					ex.message!!.contains("Paramètres")
			}
			.verify()
	}

	@Test
	fun `requireProviderReady falls back to activeLlmProvider`() {
		`when`(settingsService.getSettings()).thenReturn(
			Mono.just(settingsWithKeys(geminiMasked = "****abcd", active = LlmProvider.GEMINI)),
		)

		StepVerifier.create(service.requireProviderReady(null))
			.assertNext { assertEquals(LlmProvider.GEMINI, it) }
			.verifyComplete()
	}

	@Test
	fun `requireProviderReady accepts override when key present`() {
		`when`(settingsService.getSettings()).thenReturn(
			Mono.just(
				settingsWithKeys(
					openAiMasked = "****zzzz",
					geminiMasked = "",
					active = LlmProvider.GEMINI,
				),
			),
		)

		StepVerifier.create(service.requireProviderReady(LlmProvider.OPENAI))
			.assertNext { assertEquals(LlmProvider.OPENAI, it) }
			.verifyComplete()
	}

	@Test
	fun `missing active key without override also errors`() {
		`when`(settingsService.getSettings()).thenReturn(
			Mono.just(settingsWithKeys(geminiMasked = "", active = LlmProvider.GEMINI)),
		)

		StepVerifier.create(service.requireProviderReady(null))
			.expectErrorSatisfies { ex ->
				assertTrue(ex is AiGenerationException)
				assertTrue(ex.message!!.startsWith("Clé API GEMINI"))
			}
			.verify()
	}

	private fun settingsWithKeys(
		openAiMasked: String = "",
		anthropicMasked: String = "",
		geminiMasked: String = "",
		active: LlmProvider = LlmProvider.GEMINI,
	) = OrganizationSettingsResponse(
		companyName = "KOBE",
		companyTagline = null,
		industry = null,
		websiteUrl = null,
		contentTopic = "tips",
		contentLanguage = ContentLanguage.auto,
		brandTone = BrandTone.PROFESSIONAL,
		targetAudience = null,
		brandGuidelines = null,
		defaultHashtags = null,
		contentPillars = null,
		topicsToAvoid = null,
		contentGoals = null,
		preferredFormats = emptyList(),
		callToActionPreference = null,
		emojiPolicy = EmojiPolicy.SPARSE,
		postLengthPreference = PostLengthPreference.MEDIUM,
		mustIncludeKeywords = null,
		examplePostStyle = null,
		publishHourUtc = 9,
		timezone = "Europe/Paris",
		activeLlmProvider = active,
		openAiModel = "gpt-4.1-nano",
		anthropicModel = "claude-haiku-4-5",
		geminiModel = "gemini-3.5-flash",
		openAiKeyMasked = openAiMasked,
		anthropicKeyMasked = anthropicMasked,
		geminiKeyMasked = geminiMasked,
		linkedInOrganizationId = null,
		linkedInClientId = null,
		linkedInClientSecretMasked = "",
		activeLinkedInAccountId = null,
		schedulerEnabled = false,
		schedulerFrequency = com.revolut.agent.model.settings.SchedulerFrequency.DAILY,
		schedulerDaysOfWeek = emptyList(),
		schedulerIntervalDays = 2,
		schedulerAnchorDate = null,
		updatedAt = Instant.parse("2026-07-18T12:00:00Z"),
	)
}
