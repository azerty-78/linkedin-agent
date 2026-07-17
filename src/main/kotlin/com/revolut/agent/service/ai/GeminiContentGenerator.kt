package com.revolut.agent.service.ai

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.model.settings.LlmProvider
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Appel direct à l'API Google Generative Language (free tier AI Studio).
 */
@Component
class GeminiContentGenerator(
	private val aiModelProperties: AiModelProperties,
	webClientBuilder: WebClient.Builder,
) : AiContentGenerator {

	override val provider: LlmProvider = LlmProvider.GEMINI

	private val webClient: WebClient = webClientBuilder
		.baseUrl(aiModelProperties.gemini.apiBaseUrl.trimEnd('/'))
		.build()

	override suspend fun generate(apiKey: String, prompt: String, model: String): String {
			val response = try {
			webClient.post()
				.uri("/models/{model}:generateContent", model)
				.header("x-goog-api-key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(
					mapOf(
						"contents" to listOf(
							mapOf(
								"parts" to listOf(
									mapOf("text" to prompt),
								),
							),
						),
					),
				)
				.retrieve()
				.awaitBody<Map<String, Any>>()
		} catch (ex: WebClientResponseException) {
			throw IllegalStateException(
				"Gemini API error ${ex.statusCode.value()}: ${ex.responseBodyAsString.take(300)}",
				ex,
			)
		}

		val text = extractText(response)
		if (text.isEmpty()) {
			throw IllegalStateException("Gemini returned an empty response for model $model")
		}
		return text
	}

	@Suppress("UNCHECKED_CAST")
	private fun extractText(response: Map<String, Any>): String {
		val candidates = response["candidates"] as? List<*> ?: return ""
		return candidates.asSequence()
			.mapNotNull { it as? Map<*, *> }
			.mapNotNull { it["content"] as? Map<*, *> }
			.mapNotNull { it["parts"] as? List<*> }
			.flatMap { parts -> parts.asSequence() }
			.mapNotNull { it as? Map<*, *> }
			.mapNotNull { it["text"] as? String }
			.joinToString("")
			.trim()
	}
}
