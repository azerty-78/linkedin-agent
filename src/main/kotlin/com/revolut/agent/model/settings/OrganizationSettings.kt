package com.revolut.agent.model.settings
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "organization_settings")
data class OrganizationSettings(
	@Id
	val id: String? = null,
	val companyName: String = "",
	/** Slogan / tagline (optionnel). */
	val companyTagline: String? = null,
	/** Secteur d’activité (optionnel). */
	val industry: String? = null,
	/** Site web http(s) (optionnel). */
	val websiteUrl: String? = null,
	val contentTopic: String = "",
	val contentLanguage: ContentLanguage = ContentLanguage.auto,
	val brandTone: BrandTone = BrandTone.PROFESSIONAL,
	val targetAudience: String? = null,
	val brandGuidelines: String? = null,
	val defaultHashtags: String? = null,
	val contentPillars: String? = null,
	val topicsToAvoid: String? = null,
	val contentGoals: String? = null,
	val preferredFormats: List<ContentFormat> = emptyList(),
	val callToActionPreference: String? = null,
	val emojiPolicy: EmojiPolicy = EmojiPolicy.SPARSE,
	val postLengthPreference: PostLengthPreference = PostLengthPreference.MEDIUM,
	val mustIncludeKeywords: String? = null,
	val examplePostStyle: String? = null,
	val publishHourUtc: Int = 9,
	val timezone: String = "Africa/Douala",
	val activeLlmProvider: LlmProvider = LlmProvider.GEMINI,
	val openAiModel: String? = null,
	val anthropicModel: String? = null,
	val geminiModel: String? = null,
	val encryptedOpenAiKey: String? = null,
	val encryptedAnthropicKey: String? = null,
	val encryptedGeminiKey: String? = null,
	/** @deprecated Prefer linkedIn accounts + organizationUrn per account. */
	val linkedInOrganizationId: String? = null,
	/** Client ID app LinkedIn Developer (public) — stocké en clair. */
	val linkedInClientId: String? = null,
	/** Client secret LinkedIn — chiffré AES comme les clés LLM. */
	val encryptedLinkedInClientSecret: String? = null,
	val linkedInRedirectUri: String? = null,
	val linkedInOauthScopes: String? = null,
	/** Compte LinkedIn actif pour publication (id Mongo de linkedin_connection). */
	val activeLinkedInAccountId: String? = null,
	val schedulerEnabled: Boolean = false,
	val schedulerFrequency: SchedulerFrequency = SchedulerFrequency.DAILY,
	/** Jours ISO 1=lun … 7=dim (WEEKLY / CUSTOM_DAYS). */
	val schedulerDaysOfWeek: List<Int> = emptyList(),
	/** Intervalle pour EVERY_N_DAYS (2–30). */
	val schedulerIntervalDays: Int = 2,
	/** Ancre EVERY_N_DAYS `YYYY-MM-DD` (timezone org). */
	val schedulerAnchorDate: String? = null,
	val updatedAt: Instant = Instant.now(),
	val updatedBy: String? = null,
)
