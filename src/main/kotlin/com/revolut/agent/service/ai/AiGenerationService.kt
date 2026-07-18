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
	private val promptContextBuilder: PromptContextBuilder,
	private val contentPostProcessor: ContentPostProcessor,
	private val aiModelProperties: AiModelProperties,
	generators: List<AiContentGenerator>,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	private val generatorsByProvider: Map<LlmProvider, AiContentGenerator> =
		generators.associateBy { it.provider }

	/**
	 * Vérifie qu’une clé est configurée pour le provider (override ou activeLlmProvider).
	 * Utilisé avant de créer un run publication (422 si absente).
	 */
	fun requireProviderReady(llmProviderOverride: LlmProvider? = null): Mono<LlmProvider> =
		organizationSettingsService.getSettings()
			.flatMap { settings ->
				val provider = llmProviderOverride ?: settings.activeLlmProvider
				if (!hasConfiguredKey(settings, provider)) {
					Mono.error(
						AiGenerationException(
							"Clé API $provider manquante. Configurez-la dans Paramètres.",
						),
					)
				} else {
					Mono.just(provider)
				}
			}

	/**
	 * Génération réelle : provider = [llmProviderOverride] ou `activeLlmProvider` (scheduler / défaut).
	 * Settings rechargés à chaque appel (brief frais).
	 */
	fun generateDailyTip(llmProviderOverride: LlmProvider? = null): Mono<GeneratedContentResponse> =
		organizationSettingsService.getSettings()
			.flatMap { settings ->
				val provider = llmProviderOverride ?: settings.activeLlmProvider
				if (!hasConfiguredKey(settings, provider)) {
					return@flatMap Mono.error(
						AiGenerationException(
							"Clé API $provider manquante. Configurez-la dans Paramètres.",
						),
					)
				}
				val model = resolveConfiguredModel(settings, provider)
				val brief = promptContextBuilder.build(
					settings = settings,
					provider = provider,
					model = model,
					compact = false,
				)
				generateWithBrief(settings, brief, productionMaxTokens(provider), mode = "production")
			}

	/**
	 * Endpoint de test uniquement : modèles faibles / free, peu de tokens.
	 * Même PromptContextBuilder (brief settings), mode compact.
	 */
	fun generateDailyTipForTest(): Mono<GeneratedContentResponse> =
		organizationSettingsService.getSettings()
			.flatMap { settings ->
				val provider = resolveTestProvider(settings)
				if (!hasConfiguredKey(settings, provider)) {
					return@flatMap Mono.error(
						AiGenerationException(
							"No API key configured for test provider $provider. " +
								"For free tests, set geminiApiKey + activeLlmProvider=GEMINI in /api/settings.",
						),
					)
				}

				val model = resolveTestModel(provider)
				val brief = promptContextBuilder.build(
					settings = settings,
					provider = provider,
					model = model,
					compact = true,
				)
				generateWithBrief(settings, brief, aiModelProperties.test.maxOutputTokens, mode = "test")
					.onErrorResume { error ->
						if (!isQuotaOrRateLimit(error) || provider == LlmProvider.GEMINI) {
							return@onErrorResume Mono.error(error)
						}
						if (!hasConfiguredKey(settings, LlmProvider.GEMINI)) {
							return@onErrorResume Mono.error(
								AiGenerationException(
									"Paid provider quota exceeded during test. " +
										"Add a Gemini API key for free-tier tests (model ${aiModelProperties.test.geminiModel}).",
									error,
								),
							)
						}
						log.warn("Test generation quota on {} — falling back to weak Gemini model", provider)
						val fallbackModel = resolveTestModel(LlmProvider.GEMINI)
						val fallbackBrief = promptContextBuilder.build(
							settings = settings,
							provider = LlmProvider.GEMINI,
							model = fallbackModel,
							compact = true,
						)
						generateWithBrief(
							settings,
							fallbackBrief,
							aiModelProperties.test.maxOutputTokens,
							mode = "test-fallback",
						)
					}
			}

	private fun generateWithBrief(
		settings: OrganizationSettingsResponse,
		brief: ContentBrief,
		maxOutputTokens: Int,
		mode: String,
	): Mono<GeneratedContentResponse> {
		val generator = generatorsByProvider[brief.provider]
			?: return Mono.error(AiGenerationException("No AI content generator registered for provider ${brief.provider}"))

		val generationStartedAt = System.currentTimeMillis()
		log.info(
			"AI generation started | mode={} | provider={} | model={} | maxOutputTokens={} | topicLength={} | format={} | lang={}",
			mode,
			brief.provider,
			brief.model,
			maxOutputTokens,
			settings.contentTopic.length,
			brief.formatChosen,
			brief.language,
		)

		return organizationSettingsService.getDecryptedKeyFor(brief.provider)
			.flatMap { apiKey ->
				mono {
					generator.generate(
						AiGenerationCommand(
							apiKey = apiKey,
							systemPrompt = brief.systemPrompt,
							userPrompt = brief.userPrompt,
							model = brief.model,
							maxOutputTokens = maxOutputTokens,
						),
					)
				}.map { raw ->
					val content = contentPostProcessor.process(raw, brief)
					val durationMs = System.currentTimeMillis() - generationStartedAt
					log.info(
						"AI generation succeeded | mode={} | provider={} | model={} | contentLength={} | durationMs={}",
						mode,
						brief.provider,
						brief.model,
						content.length,
						durationMs,
					)
					GeneratedContentResponse(
						content = content,
						provider = brief.provider,
						model = brief.model,
						generatedAt = Instant.now(),
						promptUsed = brief.combinedPrompt(),
					)
				}.onErrorMap { error -> toAiGenerationException(brief.provider, brief.model, error) }
			}
	}

	/** Choix utilisateur (settings) — aucun forçage. */
	private fun resolveConfiguredModel(
		settings: OrganizationSettingsResponse,
		provider: LlmProvider,
	): String {
		val configured = when (provider) {
			LlmProvider.OPENAI -> settings.openAiModel
			LlmProvider.ANTHROPIC -> settings.anthropicModel
			LlmProvider.GEMINI -> settings.geminiModel
		}.ifBlank { LlmModelCatalog.defaultModel(provider) }

		return if (provider == LlmProvider.GEMINI) {
			LlmModelCatalog.resolveGeminiModel(configured)
		} else {
			configured
		}
	}

	/** Modèles faibles hardcodés pour /generate-test uniquement. */
	private fun resolveTestModel(provider: LlmProvider): String =
		when (provider) {
			LlmProvider.OPENAI -> aiModelProperties.test.openaiModel
			LlmProvider.ANTHROPIC -> aiModelProperties.test.anthropicModel
			LlmProvider.GEMINI -> LlmModelCatalog.resolveGeminiModel(
				aiModelProperties.test.geminiModel.ifBlank { LlmModelCatalog.defaultTestModel(LlmProvider.GEMINI) },
			)
		}

	private fun resolveTestProvider(settings: OrganizationSettingsResponse): LlmProvider {
		if (aiModelProperties.test.preferGemini && hasConfiguredKey(settings, LlmProvider.GEMINI)) {
			return LlmProvider.GEMINI
		}
		return settings.activeLlmProvider
	}

	private fun productionMaxTokens(provider: LlmProvider): Int =
		when (provider) {
			LlmProvider.OPENAI -> aiModelProperties.maxOutputTokens
			LlmProvider.ANTHROPIC -> minOf(aiModelProperties.anthropic.maxTokens, aiModelProperties.maxOutputTokens)
			LlmProvider.GEMINI -> minOf(aiModelProperties.gemini.maxOutputTokens, aiModelProperties.maxOutputTokens)
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
					"For free tests use a Gemini key (model ${aiModelProperties.test.geminiModel})."

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
