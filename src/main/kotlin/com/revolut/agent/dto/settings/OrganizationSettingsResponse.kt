package com.revolut.agent.dto.settings

import com.revolut.agent.model.settings.BrandTone
import com.revolut.agent.model.settings.ContentFormat
import com.revolut.agent.model.settings.ContentLanguage
import com.revolut.agent.model.settings.EmojiPolicy
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.PostLengthPreference
import java.time.Instant

data class OrganizationSettingsResponse(
	val companyName: String,
	val companyTagline: String?,
	val industry: String?,
	val websiteUrl: String?,
	val contentTopic: String,
	val contentLanguage: ContentLanguage,
	val brandTone: BrandTone,
	val targetAudience: String?,
	val brandGuidelines: String?,
	val defaultHashtags: String?,
	val contentPillars: String?,
	val topicsToAvoid: String?,
	val contentGoals: String?,
	val preferredFormats: List<ContentFormat>,
	val callToActionPreference: String?,
	val emojiPolicy: EmojiPolicy,
	val postLengthPreference: PostLengthPreference,
	val mustIncludeKeywords: String?,
	val examplePostStyle: String?,
	val publishHourUtc: Int,
	val timezone: String,
	val activeLlmProvider: LlmProvider,
	val openAiModel: String,
	val anthropicModel: String,
	val geminiModel: String,
	val openAiKeyMasked: String,
	val anthropicKeyMasked: String,
	val geminiKeyMasked: String,
	val linkedInOrganizationId: String?,
	/** Client ID app LinkedIn (public). */
	val linkedInClientId: String?,
	val linkedInClientSecretMasked: String,
	/** Id du compte LinkedIn actif (parmi max 3). */
	val activeLinkedInAccountId: String?,
	val schedulerEnabled: Boolean,
	val updatedAt: Instant,
)
