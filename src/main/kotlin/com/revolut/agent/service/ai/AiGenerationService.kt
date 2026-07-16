package com.revolut.agent.service.ai

import com.revolut.agent.dto.ai.GeneratedContentResponse
import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.exception.ai.AiGenerationException
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
	generators: List<AiContentGenerator>,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	private val generatorsByProvider: Map<LlmProvider, AiContentGenerator> =
		generators.associateBy { it.provider }

	fun generateDailyTip(): Mono<GeneratedContentResponse> =
		organizationSettingsService.getSettings()
			.flatMap { settings ->
				val provider = settings.activeLlmProvider
				if (!hasConfiguredKey(settings, provider)) {
					return@flatMap Mono.error(
						AiGenerationException(
							"No API key configured for provider $provider, please configure it in /api/settings",
						),
					)
				}

				val generator = generatorsByProvider[provider]
					?: return@flatMap Mono.error(
						AiGenerationException("No AI content generator registered for provider $provider"),
					)

				val prompt = promptBuilderService.buildDailyTipPrompt(settings.contentTopic)
				val generationStartedAt = System.currentTimeMillis()
				log.info(
					"AI generation started | provider={} | topicLength={} | timezone={} | publishHourUtc={}",
					provider,
					settings.contentTopic.length,
					settings.timezone,
					settings.publishHourUtc,
				)

				organizationSettingsService.getDecryptedKeyFor(provider)
					.flatMap { apiKey ->
						mono {
							generator.generate(apiKey, prompt)
						}.map { content ->
							val durationMs = System.currentTimeMillis() - generationStartedAt
							log.info(
								"AI generation succeeded | provider={} | contentLength={} | durationMs={}",
								provider,
								content.length,
								durationMs,
							)
							GeneratedContentResponse(
								content = content,
								provider = provider,
								generatedAt = Instant.now(),
								promptUsed = prompt,
							)
						}.onErrorMap { error -> toAiGenerationException(provider, error) }
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

	private fun toAiGenerationException(provider: LlmProvider, error: Throwable): Throwable {
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

			error.message?.contains("429", ignoreCase = true) == true ||
				error.message?.contains("quota", ignoreCase = true) == true ||
				error.message?.contains("rate", ignoreCase = true) == true ->
				"API quota or rate limit exceeded for provider $provider. Try again later."

			error.message?.contains("timeout", ignoreCase = true) == true ||
				root.message?.contains("timeout", ignoreCase = true) == true ->
				"Timed out while calling provider $provider. Try again later."

			else ->
				"Failed to generate content with provider $provider: ${error.message ?: "unknown error"}"
		}

		log.warn(
			"AI generation failed | provider={} | errorType={} | errorMessage={}",
			provider,
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
