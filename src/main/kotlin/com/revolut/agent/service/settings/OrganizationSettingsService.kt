package com.revolut.agent.service.settings

import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.settings.LlmModelOptionResponse
import com.revolut.agent.dto.settings.LlmModelsCatalogResponse
import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.dto.settings.UpdateOrganizationSettingsRequest
import com.revolut.agent.model.settings.ContentFormat
import com.revolut.agent.model.settings.LlmModelCatalog
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.OrganizationSettings
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

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
							val preferredFormats = resolvePreferredFormats(base.preferredFormats, request.preferredFormats)
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
									linkedInClientId = resolveOptionalClearable(base.linkedInClientId, request.linkedInClientId),
									encryptedLinkedInClientSecret = resolveLinkedInClientSecret(
										base.encryptedLinkedInClientSecret,
										request.linkedInClientSecret,
										request.clearLinkedInClientSecret == true,
									),
									// redirect / scopes : 100 % serveur (linkedin.redirect-uri / oauth-scopes) — ignore PUT
									linkedInRedirectUri = base.linkedInRedirectUri,
									linkedInOauthScopes = base.linkedInOauthScopes,
									activeLinkedInAccountId = nextActiveAccountId,
									schedulerEnabled = request.schedulerEnabled ?: base.schedulerEnabled,
									updatedAt = Instant.now(),
									updatedBy = updatedByEmail,
								),
							)
						},
					)
			}
			.map(::toResponse)

	/**
	 * Credentials app LinkedIn (client id/secret) depuis la DB — plus depuis .env.
	 */
	fun getLinkedInAppCredentials(): Mono<LinkedInAppCredentials> =
		findOrCreateSettings()
			.flatMap { settings ->
				val clientId = settings.linkedInClientId?.trim().orEmpty()
				if (clientId.isBlank()) {
					return@flatMap Mono.error(
						IllegalStateException(
							"LinkedIn client ID manquant. Configurez-le via PUT /api/settings (linkedInClientId).",
						),
					)
				}
				val secretEncrypted = settings.encryptedLinkedInClientSecret
				if (secretEncrypted.isNullOrBlank()) {
					return@flatMap Mono.error(
						IllegalStateException(
							"LinkedIn client secret manquant. Configurez-le via PUT /api/settings (linkedInClientSecret).",
						),
					)
				}
				val secret = encryptionService.tryDecrypt(secretEncrypted)
					?: return@flatMap Mono.error(
						IllegalStateException(
							"LinkedIn client secret illisible (ENCRYPTION_KEY?). Re-saisissez linkedInClientSecret.",
						),
					)
				val redirect = linkedInProperties.redirectUri
				val scopes = linkedInProperties.oauthScopes
				Mono.just(
					LinkedInAppCredentials(
						clientId = clientId,
						clientSecret = secret,
						redirectUri = redirect,
						oauthScopes = scopes,
					),
				)
			}

	/** Id du compte LinkedIn actif, ou chaîne vide s'il n'y en a pas. */
	fun getActiveLinkedInAccountId(): Mono<String> =
		findOrCreateSettings().map { it.activeLinkedInAccountId.orEmpty() }

	/** Présence Client ID + secret chiffré (sans déchiffrer). */
	fun hasLinkedInAppCredentials(): Mono<Boolean> =
		findOrCreateSettings().map { settings ->
			!settings.linkedInClientId.isNullOrBlank() &&
				!settings.encryptedLinkedInClientSecret.isNullOrBlank()
		}

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
			updatedAt = settings.updatedAt,
		)

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
