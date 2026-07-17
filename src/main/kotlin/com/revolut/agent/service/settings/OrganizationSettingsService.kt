package com.revolut.agent.service.settings

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.dto.settings.LlmModelOptionResponse
import com.revolut.agent.dto.settings.LlmModelsCatalogResponse
import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.dto.settings.UpdateOrganizationSettingsRequest
import com.revolut.agent.model.settings.LlmModelCatalog
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.OrganizationSettings
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class OrganizationSettingsService(
	private val organizationSettingsRepository: OrganizationSettingsRepository,
	private val encryptionService: EncryptionService,
	private val aiModelProperties: AiModelProperties,
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
				val updated = base.copy(
					companyName = request.companyName.trim(),
					contentTopic = request.contentTopic.trim(),
					publishHourUtc = request.publishHourUtc,
					timezone = request.timezone.trim(),
					activeLlmProvider = request.activeLlmProvider,
					openAiModel = resolveModel(base.openAiModel, request.openAiModel, LlmProvider.OPENAI),
					anthropicModel = resolveModel(base.anthropicModel, request.anthropicModel, LlmProvider.ANTHROPIC),
					geminiModel = resolveModel(base.geminiModel, request.geminiModel, LlmProvider.GEMINI),
					encryptedOpenAiKey = resolveEncryptedKey(base.encryptedOpenAiKey, request.openAiApiKey),
					encryptedAnthropicKey = resolveEncryptedKey(base.encryptedAnthropicKey, request.anthropicApiKey),
					encryptedGeminiKey = resolveEncryptedKey(base.encryptedGeminiKey, request.geminiApiKey),
					linkedInOrganizationId = request.linkedInOrganizationId?.trim()?.takeIf { it.isNotEmpty() },
					updatedAt = Instant.now(),
					updatedBy = updatedByEmail,
				)
				organizationSettingsRepository.save(updated)
			}
			.map(::toResponse)

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

	/**
	 * Drop ciphertext that cannot be decrypted with the current key
	 * (typical after an ephemeral ENCRYPTION_KEY rotation on restart).
	 */
	private fun clearUnreadableKeys(settings: OrganizationSettings): OrganizationSettings {
		val openAi = readableOrNull(settings.encryptedOpenAiKey)
		val anthropic = readableOrNull(settings.encryptedAnthropicKey)
		val gemini = readableOrNull(settings.encryptedGeminiKey)

		if (
			openAi == settings.encryptedOpenAiKey &&
			anthropic == settings.encryptedAnthropicKey &&
			gemini == settings.encryptedGeminiKey
		) {
			return settings
		}

		return settings.copy(
			encryptedOpenAiKey = openAi,
			encryptedAnthropicKey = anthropic,
			encryptedGeminiKey = gemini,
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
		when (provider) {
			LlmProvider.OPENAI -> aiModelProperties.openai.model
			LlmProvider.ANTHROPIC -> aiModelProperties.anthropic.model
			LlmProvider.GEMINI -> LlmModelCatalog.resolveGeminiModel(aiModelProperties.gemini.model)
		}

	private fun toResponse(settings: OrganizationSettings): OrganizationSettingsResponse =
		OrganizationSettingsResponse(
			companyName = settings.companyName,
			contentTopic = settings.contentTopic,
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
			schedulerEnabled = settings.schedulerEnabled,
			updatedAt = settings.updatedAt,
		)

	private fun maskEncrypted(encrypted: String?): String {
		if (encrypted.isNullOrBlank()) {
			return ""
		}
		val plain = encryptionService.tryDecrypt(encrypted) ?: return ""
		return encryptionService.mask(plain)
	}
}
