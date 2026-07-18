package com.revolut.agent.service.ai

import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.model.settings.BrandTone
import com.revolut.agent.model.settings.ContentFormat
import com.revolut.agent.model.settings.ContentLanguage
import com.revolut.agent.model.settings.EmojiPolicy
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.PostLengthPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PromptContextBuilderTest {

	private val builder = PromptContextBuilder()
	private val postProcessor = ContentPostProcessor()

	@Test
	fun `system prompt includes identity brief style and topicsToAvoid`() {
		val brief = builder.build(
			settings = sampleSettings(
				topicsToAvoid = "politique, concurrents",
				brandTone = BrandTone.CASUAL,
				emojiPolicy = EmojiPolicy.NONE,
				preferredFormats = listOf(ContentFormat.STORY, ContentFormat.TIP),
			),
			provider = LlmProvider.GEMINI,
			model = "gemini-3.5-flash",
		)

		assertTrue(brief.systemPrompt.contains("IDENTITÉ"))
		assertTrue(brief.systemPrompt.contains("KOBE Corporation"))
		assertTrue(brief.systemPrompt.contains("politique, concurrents"))
		assertTrue(brief.systemPrompt.contains("CASUAL"))
		assertTrue(brief.systemPrompt.contains("aucune"))
		assertEquals("fr", brief.language)
		assertTrue(brief.formatChosen == ContentFormat.STORY || brief.formatChosen == ContentFormat.TIP)
	}

	@Test
	fun `minimal topic-only settings still builds a prompt`() {
		val brief = builder.build(
			settings = sampleSettings(),
			provider = LlmProvider.OPENAI,
			model = "gpt-4.1-nano",
		)
		assertTrue(brief.systemPrompt.contains("BRIEF"))
		assertTrue(brief.userPrompt.contains("fintech"))
		assertEquals(PostLengthPreference.MEDIUM, PostLengthPreference.MEDIUM)
		assertEquals(900, brief.targetLength.min)
	}

	@Test
	fun `post processor appends hashtags and strips emojis when NONE`() {
		val brief = builder.build(
			settings = sampleSettings(
				defaultHashtags = "#growth #linkedin",
				emojiPolicy = EmojiPolicy.NONE,
			),
			provider = LlmProvider.GEMINI,
			model = "gemini-3.5-flash",
		)
		val processed = postProcessor.process("Un tip utile 🚀 pour grandir", brief)
		assertFalse(processed.contains("🚀"))
		assertTrue(processed.contains("#growth"))
		assertTrue(processed.contains("#linkedin"))
	}

	private fun sampleSettings(
		topicsToAvoid: String? = null,
		brandTone: BrandTone = BrandTone.PROFESSIONAL,
		emojiPolicy: EmojiPolicy = EmojiPolicy.SPARSE,
		preferredFormats: List<ContentFormat> = emptyList(),
		defaultHashtags: String? = null,
	) = OrganizationSettingsResponse(
		companyName = "KOBE Corporation",
		companyTagline = "Publish smarter",
		industry = "SaaS",
		websiteUrl = "https://example.com",
		contentTopic = "fintech tips pour PME",
		contentLanguage = ContentLanguage.auto,
		brandTone = brandTone,
		targetAudience = null,
		brandGuidelines = null,
		defaultHashtags = defaultHashtags,
		contentPillars = null,
		topicsToAvoid = topicsToAvoid,
		contentGoals = null,
		preferredFormats = preferredFormats,
		callToActionPreference = null,
		emojiPolicy = emojiPolicy,
		postLengthPreference = PostLengthPreference.MEDIUM,
		mustIncludeKeywords = null,
		examplePostStyle = null,
		publishHourUtc = 9,
		timezone = "Europe/Paris",
		activeLlmProvider = LlmProvider.GEMINI,
		openAiModel = "gpt-4.1-nano",
		anthropicModel = "claude-haiku-4-5",
		geminiModel = "gemini-3.5-flash",
		openAiKeyMasked = "",
		anthropicKeyMasked = "",
		geminiKeyMasked = "****abcd",
		linkedInOrganizationId = null,
		linkedInClientId = null,
		linkedInClientSecretMasked = "",
		activeLinkedInAccountId = null,
		schedulerEnabled = false,
		updatedAt = Instant.parse("2026-07-18T12:00:00Z"),
	)
}
