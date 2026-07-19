package com.revolut.agent.dto.settings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.revolut.agent.model.settings.BrandTone
import com.revolut.agent.model.settings.ContentFormat
import com.revolut.agent.model.settings.ContentLanguage
import com.revolut.agent.model.settings.EmojiPolicy
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.PostLengthPreference
import com.revolut.agent.model.settings.SchedulerFrequency

/**
 * Body PATCH partiel — tous les champs optionnels.
 * `null` = omis / ne pas toucher.
 * Texte optionnel `""` = vider.
 * Clé API `null` / blank = conserver le secret.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PatchOrganizationSettingsRequest(
	val companyName: String? = null,
	val companyTagline: String? = null,
	val industry: String? = null,
	val websiteUrl: String? = null,
	val contentTopic: String? = null,
	val contentLanguage: ContentLanguage? = null,
	val brandTone: BrandTone? = null,
	val targetAudience: String? = null,
	val brandGuidelines: String? = null,
	val defaultHashtags: String? = null,
	val contentPillars: String? = null,
	val topicsToAvoid: String? = null,
	val contentGoals: String? = null,
	val preferredFormats: List<ContentFormat>? = null,
	val callToActionPreference: String? = null,
	val emojiPolicy: EmojiPolicy? = null,
	val postLengthPreference: PostLengthPreference? = null,
	val mustIncludeKeywords: String? = null,
	val examplePostStyle: String? = null,
	val publishHourUtc: Int? = null,
	val timezone: String? = null,
	val activeLlmProvider: LlmProvider? = null,
	val openAiModel: String? = null,
	val anthropicModel: String? = null,
	val geminiModel: String? = null,
	val openAiApiKey: String? = null,
	val anthropicApiKey: String? = null,
	val geminiApiKey: String? = null,
	val linkedInOrganizationId: String? = null,
	val activeLinkedInAccountId: String? = null,
	val schedulerEnabled: Boolean? = null,
	val schedulerFrequency: SchedulerFrequency? = null,
	val schedulerDaysOfWeek: List<Int>? = null,
	val schedulerIntervalDays: Int? = null,
	val schedulerAnchorDate: String? = null,
)
