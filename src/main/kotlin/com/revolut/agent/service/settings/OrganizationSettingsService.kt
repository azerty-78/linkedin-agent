package com.revolut.agent.service.settings

import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.settings.LlmModelOptionResponse
import com.revolut.agent.dto.settings.LlmModelsCatalogResponse
import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.dto.settings.PatchOrganizationSettingsRequest
import com.revolut.agent.dto.settings.UpdateOrganizationSettingsRequest
import com.revolut.agent.model.settings.ContentFormat
import com.revolut.agent.model.settings.LlmModelCatalog
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.OrganizationSettings
import com.revolut.agent.model.settings.SchedulerFrequency
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import com.revolut.agent.service.linkedin.LinkedInAppService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

data class LinkedInAppCredentials(
	val clientId: String,
	val clientSecret: String,
	val redirectUri: String,
	val oauthScopes: String,
)

@Service
class OrganizationSettingsService(
	private val organizationSettingsRepository: OrganizationSettingsRepository,
	private val linkedInConnectionRepository: LinkedInConnectionRepository,
	private val encryptionService: EncryptionService,
	private val linkedInProperties: LinkedInProperties,
	@Lazy private val linkedInAppService: LinkedInAppService,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun getSettings(): Mono<OrganizationSettingsResponse> =
		findOrCreateSettings()
			.flatMap { settings ->
				val healed = clearUnreadableKeys(settings)
				if (healed != settings) {
					log.warn(
						"Cleared API keys that cannot be decrypted with the current ENCRYPTION_KEY. " +
							"Re-enter them via PUT /api/settings.",
					)
					organizationSettingsRepository.save(healed)
				} else {
					Mono.just(settings)
				}
			}
			.map(::toResponse)
			.flatMap(::overlayActiveLinkedInApp)

	fun listAvailableModels(): LlmModelsCatalogResponse =
		LlmModelsCatalogResponse(
			models = LlmModelCatalog.models.map {
				LlmModelOptionResponse(
					id = it.id,
					label = it.label,
					provider = it.provider,
					freeTier = it.freeTier,
					recommendedForTests = it.recommendedForTests,
				)
			},
			defaultByProvider = LlmProvider.entries.associateWith { fallbackModel(it) },
			recommendedTestByProvider = LlmProvider.entries.associateWith {
				LlmModelCatalog.recommendedTestModel(it)
			},
		)

	fun updateSettings(
		request: UpdateOrganizationSettingsRequest,
		updatedByEmail: String,
	): Mono<OrganizationSettingsResponse> =
		findOrCreateSettings()
			.flatMap { existing ->
				val base = clearUnreadableKeys(existing)
				val nextActiveAccountId = when {
					request.activeLinkedInAccountId == null -> base.activeLinkedInAccountId
					request.activeLinkedInAccountId.isBlank() -> null
					else -> request.activeLinkedInAccountId.trim()
				}
				validateActiveLinkedInAccountId(nextActiveAccountId)
					.then(
						Mono.defer {
							val websiteUrl = normalizeOptionalText(request.websiteUrl, maxLen = 500)
							validateWebsiteUrl(websiteUrl)
							validateTimezone(request.timezone.trim())
							val preferredFormats = resolvePreferredFormats(base.preferredFormats, request.preferredFormats)
							val frequency = request.schedulerFrequency ?: base.schedulerFrequency
							val daysOfWeek = resolveSchedulerDays(base.schedulerDaysOfWeek, request.schedulerDaysOfWeek)
							val intervalDays = request.schedulerIntervalDays ?: base.schedulerIntervalDays
							val anchorDate = resolveSchedulerAnchor(base.schedulerAnchorDate, request.schedulerAnchorDate)
							validateSchedulerConfig(frequency, daysOfWeek, intervalDays)
							organizationSettingsRepository.save(
								base.copy(
									companyName = request.companyName.trim(),
									companyTagline = normalizeOptionalText(request.companyTagline, maxLen = 120),
									industry = normalizeOptionalText(request.industry, maxLen = 120),
									websiteUrl = websiteUrl,
									contentTopic = request.contentTopic.trim(),
									contentLanguage = request.contentLanguage ?: base.contentLanguage,
									brandTone = request.brandTone ?: base.brandTone,
									targetAudience = normalizeOptionalText(request.targetAudience, maxLen = 2000),
									brandGuidelines = normalizeOptionalText(request.brandGuidelines, maxLen = 4000),
									defaultHashtags = normalizeOptionalText(request.defaultHashtags, maxLen = 300),
									contentPillars = normalizeOptionalText(request.contentPillars, maxLen = 2000),
									topicsToAvoid = normalizeOptionalText(request.topicsToAvoid, maxLen = 2000),
									contentGoals = normalizeOptionalText(request.contentGoals, maxLen = 2000),
									preferredFormats = preferredFormats,
									callToActionPreference = normalizeOptionalText(request.callToActionPreference, maxLen = 500),
									emojiPolicy = request.emojiPolicy ?: base.emojiPolicy,
									postLengthPreference = request.postLengthPreference ?: base.postLengthPreference,
									mustIncludeKeywords = normalizeOptionalText(request.mustIncludeKeywords, maxLen = 500),
									examplePostStyle = normalizeOptionalText(request.examplePostStyle, maxLen = 4000),
									publishHourUtc = request.publishHourUtc,
									timezone = request.timezone.trim(),
									activeLlmProvider = request.activeLlmProvider,
									openAiModel = resolveModel(base.openAiModel, request.openAiModel, LlmProvider.OPENAI),
									anthropicModel = resolveModel(base.anthropicModel, request.anthropicModel, LlmProvider.ANTHROPIC),
									geminiModel = resolveModel(base.geminiModel, request.geminiModel, LlmProvider.GEMINI),
									encryptedOpenAiKey = resolveEncryptedKey(base.encryptedOpenAiKey, request.openAiApiKey),
									encryptedAnthropicKey = resolveEncryptedKey(base.encryptedAnthropicKey, request.anthropicApiKey),
									encryptedGeminiKey = resolveEncryptedKey(base.encryptedGeminiKey, request.geminiApiKey),
									linkedInOrganizationId = request.linkedInOrganizationId?.trim()?.takeIf { it.isNotEmpty() }
										?: base.linkedInOrganizationId,
									// Credentials LinkedIn : gérés via /api/linkedin/apps — écriture settings ignorée
									linkedInClientId = base.linkedInClientId,
									encryptedLinkedInClientSecret = base.encryptedLinkedInClientSecret,
									// redirect / scopes : 100 % serveur (linkedin.redirect-uri / oauth-scopes) — ignore PUT
									linkedInRedirectUri = base.linkedInRedirectUri,
									linkedInOauthScopes = base.linkedInOauthScopes,
									activeLinkedInAccountId = nextActiveAccountId,
									schedulerEnabled = request.schedulerEnabled ?: base.schedulerEnabled,
									schedulerFrequency = frequency,
									schedulerDaysOfWeek = daysOfWeek,
									schedulerIntervalDays = intervalDays,
									schedulerAnchorDate = anchorDate,
									updatedAt = Instant.now(),
									updatedBy = updatedByEmail,
								),
							)
						},
					)
			}
			.map(::toResponse)
			.flatMap(::overlayActiveLinkedInApp)

	/**
	 * Mise à jour partielle : champs `null` du DTO = omis (conserver).
	 * `companyName` / `contentTopic` validés uniquement s’ils sont non-null.
	 */
	fun patchSettings(
		request: PatchOrganizationSettingsRequest,
		updatedByEmail: String,
	): Mono<OrganizationSettingsResponse> =
		findOrCreateSettings()
			.flatMap { existing ->
				val base = clearUnreadableKeys(existing)
				Mono.defer {
					val patched = applyPatch(base, request)
					val accountCheck =
						if (request.activeLinkedInAccountId != null) {
							validateActiveLinkedInAccountId(patched.activeLinkedInAccountId)
						} else {
							Mono.empty()
						}
					accountCheck.then(
						organizationSettingsRepository.save(
							patched.copy(
								updatedAt = Instant.now(),
								updatedBy = updatedByEmail,
							),
						),
					)
				}
			}
			.map(::toResponse)
			.flatMap(::overlayActiveLinkedInApp)

	private fun applyPatch(
		base: OrganizationSettings,
		request: PatchOrganizationSettingsRequest,
	): OrganizationSettings {
		var next = base
		var schedulerTouched = false

		request.companyName?.let { raw ->
			val name = raw.trim()
			if (name.isEmpty()) {
				throw IllegalArgumentException("companyName must not be blank")
			}
			if (name.length > 200) {
				throw IllegalArgumentException("companyName must be at most 200 characters")
			}
			next = next.copy(companyName = name)
		}
		request.companyTagline?.let {
			next = next.copy(companyTagline = normalizeOptionalText(it, 120))
		}
		request.industry?.let {
			next = next.copy(industry = normalizeOptionalText(it, 120))
		}
		request.websiteUrl?.let {
			val websiteUrl = normalizeOptionalText(it, 500)
			validateWebsiteUrl(websiteUrl)
			next = next.copy(websiteUrl = websiteUrl)
		}
		request.contentTopic?.let { raw ->
			val topic = raw.trim()
			if (topic.isEmpty()) {
				throw IllegalArgumentException("contentTopic must not be blank")
			}
			if (topic.length > 2000) {
				throw IllegalArgumentException("contentTopic must be at most 2000 characters")
			}
			next = next.copy(contentTopic = topic)
		}
		request.contentLanguage?.let { next = next.copy(contentLanguage = it) }
		request.brandTone?.let { next = next.copy(brandTone = it) }
		request.targetAudience?.let {
			next = next.copy(targetAudience = normalizeOptionalText(it, 2000))
		}
		request.brandGuidelines?.let {
			next = next.copy(brandGuidelines = normalizeOptionalText(it, 4000))
		}
		request.defaultHashtags?.let {
			next = next.copy(defaultHashtags = normalizeOptionalText(it, 300))
		}
		request.contentPillars?.let {
			next = next.copy(contentPillars = normalizeOptionalText(it, 2000))
		}
		request.topicsToAvoid?.let {
			next = next.copy(topicsToAvoid = normalizeOptionalText(it, 2000))
		}
		request.contentGoals?.let {
			next = next.copy(contentGoals = normalizeOptionalText(it, 2000))
		}
		request.preferredFormats?.let {
			next = next.copy(preferredFormats = resolvePreferredFormats(base.preferredFormats, it))
		}
		request.callToActionPreference?.let {
			next = next.copy(callToActionPreference = normalizeOptionalText(it, 500))
		}
		request.emojiPolicy?.let { next = next.copy(emojiPolicy = it) }
		request.postLengthPreference?.let { next = next.copy(postLengthPreference = it) }
		request.mustIncludeKeywords?.let {
			next = next.copy(mustIncludeKeywords = normalizeOptionalText(it, 500))
		}
		request.examplePostStyle?.let {
			next = next.copy(examplePostStyle = normalizeOptionalText(it, 4000))
		}
		request.publishHourUtc?.let { hour ->
			if (hour !in 0..23) {
				throw IllegalArgumentException("publishHourUtc must be between 0 and 23")
			}
			next = next.copy(publishHourUtc = hour)
			schedulerTouched = true
		}
		request.timezone?.let { raw ->
			val tz = raw.trim()
			if (tz.isEmpty()) {
				throw IllegalArgumentException("timezone must not be blank")
			}
			validateTimezone(tz)
			next = next.copy(timezone = tz)
			schedulerTouched = true
		}
		request.activeLlmProvider?.let { next = next.copy(activeLlmProvider = it) }
		request.openAiModel?.let {
			next = next.copy(openAiModel = resolveModel(base.openAiModel, it, LlmProvider.OPENAI))
		}
		request.anthropicModel?.let {
			next = next.copy(anthropicModel = resolveModel(base.anthropicModel, it, LlmProvider.ANTHROPIC))
		}
		request.geminiModel?.let {
			next = next.copy(geminiModel = resolveModel(base.geminiModel, it, LlmProvider.GEMINI))
		}
		// Clés : null/blank = conserver (resolveEncryptedKey)
		if (request.openAiApiKey != null) {
			next = next.copy(encryptedOpenAiKey = resolveEncryptedKey(base.encryptedOpenAiKey, request.openAiApiKey))
		}
		if (request.anthropicApiKey != null) {
			next = next.copy(
				encryptedAnthropicKey = resolveEncryptedKey(base.encryptedAnthropicKey, request.anthropicApiKey),
			)
		}
		if (request.geminiApiKey != null) {
			next = next.copy(encryptedGeminiKey = resolveEncryptedKey(base.encryptedGeminiKey, request.geminiApiKey))
		}
		request.linkedInOrganizationId?.let { raw ->
			next = next.copy(linkedInOrganizationId = raw.trim().takeIf { it.isNotEmpty() })
		}
		request.activeLinkedInAccountId?.let { raw ->
			next = next.copy(activeLinkedInAccountId = raw.trim().takeIf { it.isNotEmpty() })
		}
		request.schedulerEnabled?.let {
			next = next.copy(schedulerEnabled = it)
			schedulerTouched = true
		}
		request.schedulerFrequency?.let {
			next = next.copy(schedulerFrequency = it)
			schedulerTouched = true
		}
		request.schedulerDaysOfWeek?.let {
			next = next.copy(schedulerDaysOfWeek = resolveSchedulerDays(base.schedulerDaysOfWeek, it))
			schedulerTouched = true
		}
		request.schedulerIntervalDays?.let {
			next = next.copy(schedulerIntervalDays = it)
			schedulerTouched = true
		}
		request.schedulerAnchorDate?.let {
			next = next.copy(schedulerAnchorDate = resolveSchedulerAnchor(base.schedulerAnchorDate, it))
			schedulerTouched = true
		}

		if (schedulerTouched) {
			validateSchedulerConfig(
				next.schedulerFrequency,
				next.schedulerDaysOfWeek,
				next.schedulerIntervalDays,
			)
		}
		return next
	}

	/**
	 * Credentials de l’**app LinkedIn active** (`/api/linkedin/apps`).
	 * Les champs settings `linkedInClientId/Secret` sont legacy (écriture ignorée).
	 */
	fun getLinkedInAppCredentials(): Mono<LinkedInAppCredentials> =
		linkedInAppService.getActiveCredentials()

	/** Id du compte LinkedIn actif, ou chaîne vide s'il n'y en a pas. */
	fun getActiveLinkedInAccountId(): Mono<String> =
		findOrCreateSettings().map { it.activeLinkedInAccountId.orEmpty() }

	/** Présence d’une app active avec secret. */
	fun hasLinkedInAppCredentials(): Mono<Boolean> =
		linkedInAppService.hasActiveAppWithSecret()

	fun setActiveLinkedInAccountId(accountId: String?): Mono<OrganizationSettings> =
		validateActiveLinkedInAccountId(accountId)
			.then(findOrCreateSettings())
			.flatMap { settings ->
				organizationSettingsRepository.save(
					settings.copy(
						activeLinkedInAccountId = accountId,
						updatedAt = Instant.now(),
					),
				)
			}

	private fun validateActiveLinkedInAccountId(accountId: String?): Mono<Void> {
		if (accountId.isNullOrBlank()) {
			return Mono.empty()
		}
		return linkedInConnectionRepository.findById(accountId)
			.switchIfEmpty(
				Mono.error(
					IllegalArgumentException("LinkedIn account not found: $accountId"),
				),
			)
			.then()
	}

	internal fun getDecryptedKeyFor(provider: LlmProvider): Mono<String> =
		findOrCreateSettings()
			.flatMap { settings ->
				val encryptedKey = when (provider) {
					LlmProvider.OPENAI -> settings.encryptedOpenAiKey
					LlmProvider.ANTHROPIC -> settings.encryptedAnthropicKey
					LlmProvider.GEMINI -> settings.encryptedGeminiKey
				}

				if (encryptedKey.isNullOrBlank()) {
					Mono.error(IllegalStateException("No API key configured for provider $provider"))
				} else {
					val plain = encryptionService.tryDecrypt(encryptedKey)
					if (plain == null) {
						Mono.error(
							IllegalStateException(
								"Stored API key for $provider cannot be decrypted. " +
									"ENCRYPTION_KEY likely changed — re-enter the key in /api/settings.",
							),
						)
					} else {
						Mono.just(plain)
					}
				}
			}

	internal fun resolveActiveModel(settings: OrganizationSettingsResponse): String =
		when (settings.activeLlmProvider) {
			LlmProvider.OPENAI -> settings.openAiModel.ifBlank { fallbackModel(LlmProvider.OPENAI) }
			LlmProvider.ANTHROPIC -> settings.anthropicModel.ifBlank { fallbackModel(LlmProvider.ANTHROPIC) }
			LlmProvider.GEMINI -> settings.geminiModel.ifBlank { fallbackModel(LlmProvider.GEMINI) }
		}

	private fun findOrCreateSettings(): Mono<OrganizationSettings> =
		organizationSettingsRepository.findFirstByOrderByUpdatedAtDesc()
			.switchIfEmpty(
				Mono.defer {
					organizationSettingsRepository.save(OrganizationSettings())
				},
			)

	private fun clearUnreadableKeys(settings: OrganizationSettings): OrganizationSettings {
		val openAi = readableOrNull(settings.encryptedOpenAiKey)
		val anthropic = readableOrNull(settings.encryptedAnthropicKey)
		val gemini = readableOrNull(settings.encryptedGeminiKey)
		val linkedInSecret = readableOrNull(settings.encryptedLinkedInClientSecret)

		if (
			openAi == settings.encryptedOpenAiKey &&
			anthropic == settings.encryptedAnthropicKey &&
			gemini == settings.encryptedGeminiKey &&
			linkedInSecret == settings.encryptedLinkedInClientSecret
		) {
			return settings
		}

		return settings.copy(
			encryptedOpenAiKey = openAi,
			encryptedAnthropicKey = anthropic,
			encryptedGeminiKey = gemini,
			encryptedLinkedInClientSecret = linkedInSecret,
			updatedAt = Instant.now(),
		)
	}

	private fun readableOrNull(encrypted: String?): String? {
		if (encrypted.isNullOrBlank()) {
			return null
		}
		return if (encryptionService.tryDecrypt(encrypted) != null) encrypted else null
	}

	private fun resolveEncryptedKey(existingEncrypted: String?, incomingPlain: String?): String? =
		incomingPlain?.trim()?.takeIf { it.isNotEmpty() }?.let(encryptionService::encrypt)
			?: existingEncrypted

	/**
	 * `null` incoming → keep existing.
	 * blank incoming → clear (`null`).
	 * non-blank → replace.
	 */
	private fun resolveOptionalClearable(existing: String?, incoming: String?): String? =
		when {
			incoming == null -> existing
			incoming.isBlank() -> null
			else -> incoming.trim()
		}

	private fun resolveLinkedInClientSecret(
		existingEncrypted: String?,
		incomingPlain: String?,
		clear: Boolean,
	): String? {
		if (clear) {
			return null
		}
		return resolveEncryptedKey(existingEncrypted, incomingPlain)
	}

	private fun resolveModel(existing: String?, incoming: String?, provider: LlmProvider): String {
		val candidate = incoming?.trim()?.takeIf { it.isNotEmpty() }
			?: existing?.trim()?.takeIf { it.isNotEmpty() }
		val resolved = candidate ?: fallbackModel(provider)
		return if (provider == LlmProvider.GEMINI) {
			LlmModelCatalog.resolveGeminiModel(resolved)
		} else {
			resolved
		}
	}

	private fun fallbackModel(provider: LlmProvider): String =
		LlmModelCatalog.defaultModel(provider)

	private fun toResponse(settings: OrganizationSettings): OrganizationSettingsResponse =
		OrganizationSettingsResponse(
			companyName = settings.companyName,
			companyTagline = settings.companyTagline,
			industry = settings.industry,
			websiteUrl = settings.websiteUrl,
			contentTopic = settings.contentTopic,
			contentLanguage = settings.contentLanguage,
			brandTone = settings.brandTone,
			targetAudience = settings.targetAudience,
			brandGuidelines = settings.brandGuidelines,
			defaultHashtags = settings.defaultHashtags,
			contentPillars = settings.contentPillars,
			topicsToAvoid = settings.topicsToAvoid,
			contentGoals = settings.contentGoals,
			preferredFormats = settings.preferredFormats.distinct().take(6),
			callToActionPreference = settings.callToActionPreference,
			emojiPolicy = settings.emojiPolicy,
			postLengthPreference = settings.postLengthPreference,
			mustIncludeKeywords = settings.mustIncludeKeywords,
			examplePostStyle = settings.examplePostStyle,
			publishHourUtc = settings.publishHourUtc,
			timezone = settings.timezone,
			activeLlmProvider = settings.activeLlmProvider,
			openAiModel = settings.openAiModel?.takeIf { it.isNotBlank() } ?: fallbackModel(LlmProvider.OPENAI),
			anthropicModel = settings.anthropicModel?.takeIf { it.isNotBlank() } ?: fallbackModel(LlmProvider.ANTHROPIC),
			geminiModel = LlmModelCatalog.resolveGeminiModel(
				settings.geminiModel?.takeIf { it.isNotBlank() } ?: fallbackModel(LlmProvider.GEMINI),
			),
			openAiKeyMasked = maskEncrypted(settings.encryptedOpenAiKey),
			anthropicKeyMasked = maskEncrypted(settings.encryptedAnthropicKey),
			geminiKeyMasked = maskEncrypted(settings.encryptedGeminiKey),
			linkedInOrganizationId = settings.linkedInOrganizationId,
			linkedInClientId = settings.linkedInClientId,
			linkedInClientSecretMasked = maskEncrypted(settings.encryptedLinkedInClientSecret),
			activeLinkedInAccountId = settings.activeLinkedInAccountId,
			schedulerEnabled = settings.schedulerEnabled,
			schedulerFrequency = settings.schedulerFrequency,
			schedulerDaysOfWeek = settings.schedulerDaysOfWeek,
			schedulerIntervalDays = settings.schedulerIntervalDays,
			schedulerAnchorDate = settings.schedulerAnchorDate,
			updatedAt = settings.updatedAt,
		)

	/** Affiche Client ID / secret masqué de l’app active (source de vérité multi-apps). */
	private fun overlayActiveLinkedInApp(
		response: OrganizationSettingsResponse,
	): Mono<OrganizationSettingsResponse> =
		linkedInAppService.listApps()
			.map { list ->
				val active = list.apps.firstOrNull { it.isActive } ?: return@map response
				response.copy(
					linkedInClientId = active.clientId,
					linkedInClientSecretMasked = active.clientSecretMasked,
				)
			}

	/**
	 * Persiste l’ancre EVERY_N_DAYS si absente (appelé par le scheduler).
	 */
	fun ensureSchedulerAnchorDate(anchorIsoDate: String): Mono<OrganizationSettingsResponse> =
		findOrCreateSettings()
			.flatMap { settings ->
				if (!settings.schedulerAnchorDate.isNullOrBlank()) {
					Mono.just(settings)
				} else {
					organizationSettingsRepository.save(
						settings.copy(
							schedulerAnchorDate = anchorIsoDate,
							updatedAt = Instant.now(),
						),
					)
				}
			}
			.map(::toResponse)

	private fun resolvePreferredFormats(
		existing: List<ContentFormat>,
		incoming: List<ContentFormat>?,
	): List<ContentFormat> {
		if (incoming == null) {
			return existing.distinct().take(6)
		}
		val distinct = incoming.distinct()
		if (distinct.size > 6) {
			throw IllegalArgumentException("preferredFormats: max 6 unique values")
		}
		return distinct
	}

	private fun resolveSchedulerDays(existing: List<Int>, incoming: List<Int>?): List<Int> {
		if (incoming == null) {
			return existing.distinct().sorted()
		}
		return incoming.distinct().sorted()
	}

	private fun resolveSchedulerAnchor(existing: String?, incoming: String?): String? {
		if (incoming == null) {
			return existing
		}
		val trimmed = incoming.trim()
		if (trimmed.isEmpty()) {
			return null
		}
		return try {
			LocalDate.parse(trimmed).toString()
		} catch (_: DateTimeParseException) {
			throw IllegalArgumentException("schedulerAnchorDate must be YYYY-MM-DD")
		}
	}

	private fun validateSchedulerConfig(
		frequency: SchedulerFrequency,
		daysOfWeek: List<Int>,
		intervalDays: Int,
	) {
		if (daysOfWeek.any { it !in 1..7 }) {
			throw IllegalArgumentException("schedulerDaysOfWeek: each day must be in 1..7 (ISO)")
		}
		when (frequency) {
			SchedulerFrequency.WEEKLY -> {
				if (daysOfWeek.size != 1) {
					throw IllegalArgumentException("WEEKLY requires exactly 1 day in schedulerDaysOfWeek")
				}
			}
			SchedulerFrequency.CUSTOM_DAYS -> {
				if (daysOfWeek.isEmpty()) {
					throw IllegalArgumentException("CUSTOM_DAYS requires at least 1 day in schedulerDaysOfWeek")
				}
			}
			SchedulerFrequency.EVERY_N_DAYS -> {
				if (intervalDays !in 2..30) {
					throw IllegalArgumentException("EVERY_N_DAYS requires schedulerIntervalDays in 2..30")
				}
			}
			SchedulerFrequency.DAILY, SchedulerFrequency.WEEKDAYS -> Unit
		}
	}

	private fun validateTimezone(timezone: String) {
		try {
			ZoneId.of(timezone)
		} catch (_: Exception) {
			throw IllegalArgumentException("timezone must be a valid IANA zone (e.g. Europe/Paris)")
		}
	}

	/** `null` / blank → null (vider) ; sinon trim + max length. */
	private fun normalizeOptionalText(value: String?, maxLen: Int): String? {
		val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
		if (trimmed.length > maxLen) {
			throw IllegalArgumentException("Value must be at most $maxLen characters")
		}
		return trimmed
	}

	private fun validateWebsiteUrl(url: String?) {
		if (url.isNullOrBlank()) {
			return
		}
		val uri = try {
			java.net.URI(url)
		} catch (_: Exception) {
			throw IllegalArgumentException("websiteUrl must be a valid absolute http(s) URL")
		}
		val scheme = uri.scheme?.lowercase()
		if (scheme != "http" && scheme != "https") {
			throw IllegalArgumentException("websiteUrl must start with http:// or https://")
		}
		if (uri.host.isNullOrBlank()) {
			throw IllegalArgumentException("websiteUrl must include a host")
		}
	}

	private fun maskEncrypted(encrypted: String?): String {
		if (encrypted.isNullOrBlank()) {
			return ""
		}
		val plain = encryptionService.tryDecrypt(encrypted) ?: return ""
		return encryptionService.mask(plain)
	}
}
