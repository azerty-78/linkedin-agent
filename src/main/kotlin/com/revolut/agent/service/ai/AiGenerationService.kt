package com.revolut.agent.service.ai

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.dto.ai.GeneratedContentResponse
import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.exception.ai.AiGenerationException
import com.revolut.agent.model.settings.LlmModelCatalog
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.service.settings.OrganizationSettingsService
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class AiGenerationService(
	private val organizationSettingsService: OrganizationSettingsService,
	private val promptBuilderService: PromptBuilderService,
	private val aiModelProperties: AiModelProperties,
	generators: List<AiContentGenerator>,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	private val generatorsByProvider: Map<LlmProvider, AiContentGenerator> =
		generators.associateBy { it.provider }

	fun generateDailyTip(): Mono<GeneratedContentResponse> =
		organizationSettingsService.getSettings()
			.flatMap { settings ->
				val primary = settings.activeLlmProvider
				if (!hasConfiguredKey(settings, primary)) {
					return@flatMap Mono.error(
						AiGenerationException(
							"No API key configured for provider $primary, please configure it in /api/settings",
						),
					)
				}

				generateWithProvider(settings, primary)
					.onErrorResume { error ->
						if (!isQuotaOrRateLimit(error) || primary == LlmProvider.GEMINI) {
							return@onErrorResume Mono.error(error)
						}
						if (!hasConfiguredKey(settings, LlmProvider.GEMINI)) {
							return@onErrorResume Mono.error(
								AiGenerationException(
									"OpenAI/Anthropic quota exceeded (no free API). " +
										"Add a Gemini API key and set activeLlmProvider=GEMINI " +
										"(model gemini-2.0-flash is free-tier) for tests.",
									error,
								),
							)
						}
						log.warn(
							"Provider {} hit quota/rate-limit — falling back to Gemini free tier for this test",
							primary,
						)
						generateWithProvider(settings, LlmProvider.GEMINI)
					}
			}

	private fun generateWithProvider(
		settings: OrganizationSettingsResponse,
		provider: LlmProvider,
	): Mono<GeneratedContentResponse> {
		val generator = generatorsByProvider[provider]
			?: return Mono.error(AiGenerationException("No AI content generator registered for provider $provider"))

		val model = resolveEconomicalModel(settings, provider)
		val prompt = promptBuilderService.buildDailyTipPrompt(settings.contentTopic)
		val generationStartedAt = System.currentTimeMillis()
		log.info(
			"AI generation started | provider={} | model={} | maxOutputTokens={} | topicLength={}",
			provider,
			model,
			aiModelProperties.maxOutputTokens,
			settings.contentTopic.length,
		)

		return organizationSettingsService.getDecryptedKeyFor(provider)
			.flatMap { apiKey ->
				mono {
					generator.generate(apiKey, prompt, model)
				}.map { content ->
					val durationMs = System.currentTimeMillis() - generationStartedAt
					log.info(
						"AI generation succeeded | provider={} | model={} | contentLength={} | durationMs={}",
						provider,
						model,
						content.length,
						durationMs,
					)
					GeneratedContentResponse(
						content = content,
						provider = provider,
						model = model,
						generatedAt = Instant.now(),
						promptUsed = prompt,
					)
				}.onErrorMap { error -> toAiGenerationException(provider, model, error) }
			}
	}

	/**
	 * OpenAI n'a pas de modèle API gratuit : on force nano/mini.
	 * Gemini : free tier flash.
	 */
	private fun resolveEconomicalModel(
		settings: OrganizationSettingsResponse,
		provider: LlmProvider,
	): String {
		val configured = when (provider) {
			LlmProvider.OPENAI -> settings.openAiModel
			LlmProvider.ANTHROPIC -> settings.anthropicModel
			LlmProvider.GEMINI -> settings.geminiModel
		}.ifBlank { LlmModelCatalog.defaultModel(provider) }

		return when (provider) {
			LlmProvider.OPENAI -> {
				val id = configured.lowercase()
				when {
					id.contains("nano") || id.contains("mini") -> configured
					else -> aiModelProperties.openai.model
				}
			}
			LlmProvider.ANTHROPIC -> {
				if (configured.lowercase().contains("haiku")) configured
				else aiModelProperties.anthropic.model
			}
			LlmProvider.GEMINI -> {
				if (configured.lowercase().contains("flash")) configured
				else aiModelProperties.gemini.model
			}
		}
	}

	private fun hasConfiguredKey(
		settings: OrganizationSettingsResponse,
		provider: LlmProvider,
	): Boolean =
		when (provider) {
			LlmProvider.OPENAI -> settings.openAiKeyMasked.isNotBlank()
			LlmProvider.ANTHROPIC -> settings.anthropicKeyMasked.isNotBlank()
			LlmProvider.GEMINI -> settings.geminiKeyMasked.isNotBlank()
		}

	private fun isQuotaOrRateLimit(error: Throwable): Boolean {
		val messages = sequenceOf(error.message, error.cause?.message)
			.filterNotNull()
			.joinToString(" ")
			.lowercase()
		return messages.contains("429") ||
			messages.contains("quota") ||
			messages.contains("rate limit") ||
			messages.contains("rate_limit")
	}

	private fun toAiGenerationException(provider: LlmProvider, model: String, error: Throwable): Throwable {
		if (error is AiGenerationException) {
			return error
		}

		val root = error.cause ?: error
		val message = when {
			error is UnsupportedOperationException || root is UnsupportedOperationException ->
				error.message ?: root.message
					?: "Provider $provider is not supported"

			error.message?.contains("401", ignoreCase = true) == true ||
				error.message?.contains("unauthorized", ignoreCase = true) == true ||
				error.message?.contains("invalid", ignoreCase = true) == true &&
				error.message?.contains("key", ignoreCase = true) == true ||
				root.message?.contains("401", ignoreCase = true) == true ->
				"Invalid or unauthorized API key for provider $provider. Update it in /api/settings."

			isQuotaOrRateLimit(error) ->
				"API quota exceeded for $provider (model=$model). " +
					"OpenAI/Anthropic require billing credit. For free tests use GEMINI + gemini-2.0-flash."

			error.message?.contains("timeout", ignoreCase = true) == true ||
				root.message?.contains("timeout", ignoreCase = true) == true ->
				"Timed out while calling provider $provider (model=$model). Try again later."

			else ->
				"Failed to generate content with provider $provider (model=$model): ${error.message ?: "unknown error"}"
		}

		log.warn(
			"AI generation failed | provider={} | model={} | errorType={} | errorMessage={}",
			provider,
			model,
			error.javaClass.simpleName,
			truncate(error.message ?: root.message),
		)
		return AiGenerationException(message, error)
	}

	private fun truncate(value: String?, maxLength: Int = 200): String {
		if (value.isNullOrBlank()) {
			return "unknown error"
		}
		return if (value.length <= maxLength) value else value.take(maxLength) + "..."
	}
}
