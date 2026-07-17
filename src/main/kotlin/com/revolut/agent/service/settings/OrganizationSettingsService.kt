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
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class OrganizationSettingsService(
	private val organizationSettingsRepository: OrganizationSettingsRepository,
	private val encryptionService: EncryptionService,
	private val aiModelProperties: AiModelProperties,
) {

	fun getSettings(): Mono<OrganizationSettingsResponse> =
		findOrCreateSettings()
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
				val updated = existing.copy(
					companyName = request.companyName.trim(),
					contentTopic = request.contentTopic.trim(),
					publishHourUtc = request.publishHourUtc,
					timezone = request.timezone.trim(),
					activeLlmProvider = request.activeLlmProvider,
					openAiModel = resolveModel(existing.openAiModel, request.openAiModel, LlmProvider.OPENAI),
					anthropicModel = resolveModel(existing.anthropicModel, request.anthropicModel, LlmProvider.ANTHROPIC),
					geminiModel = resolveModel(existing.geminiModel, request.geminiModel, LlmProvider.GEMINI),
					encryptedOpenAiKey = resolveEncryptedKey(existing.encryptedOpenAiKey, request.openAiApiKey),
					encryptedAnthropicKey = resolveEncryptedKey(existing.encryptedAnthropicKey, request.anthropicApiKey),
					encryptedGeminiKey = resolveEncryptedKey(existing.encryptedGeminiKey, request.geminiApiKey),
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
					Mono.fromCallable { encryptionService.decrypt(encryptedKey) }
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

	private fun resolveEncryptedKey(existingEncrypted: String?, incomingPlain: String?): String? =
		incomingPlain?.trim()?.takeIf { it.isNotEmpty() }?.let(encryptionService::encrypt)
			?: existingEncrypted

	private fun resolveModel(existing: String?, incoming: String?, provider: LlmProvider): String {
		val candidate = incoming?.trim()?.takeIf { it.isNotEmpty() }
			?: existing?.trim()?.takeIf { it.isNotEmpty() }
		return candidate ?: fallbackModel(provider)
	}

	private fun fallbackModel(provider: LlmProvider): String =
		when (provider) {
			LlmProvider.OPENAI -> aiModelProperties.openai.model
			LlmProvider.ANTHROPIC -> aiModelProperties.anthropic.model
			LlmProvider.GEMINI -> aiModelProperties.gemini.model
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
			geminiModel = settings.geminiModel?.takeIf { it.isNotBlank() } ?: fallbackModel(LlmProvider.GEMINI),
			openAiKeyMasked = maskEncrypted(settings.encryptedOpenAiKey),
			anthropicKeyMasked = maskEncrypted(settings.encryptedAnthropicKey),
			geminiKeyMasked = maskEncrypted(settings.encryptedGeminiKey),
			linkedInOrganizationId = settings.linkedInOrganizationId,
			schedulerEnabled = settings.schedulerEnabled,
			updatedAt = settings.updatedAt,
		)

	private fun maskEncrypted(encrypted: String?): String =
		if (encrypted.isNullOrBlank()) {
			""
		} else {
			encryptionService.mask(encryptionService.decrypt(encrypted))
		}
}
