package com.revolut.agent.service.ai

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.model.settings.LlmModelCatalog
import com.revolut.agent.model.settings.LlmProvider
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.util.UriComponentsBuilder

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
		val resolvedModel = LlmModelCatalog.resolveGeminiModel(model)
		val maxOutput = minOf(aiModelProperties.gemini.maxOutputTokens, aiModelProperties.maxOutputTokens)

		val response = try {
			val uri = UriComponentsBuilder
				.fromPath("/models/{model}:generateContent")
				.queryParam("key", apiKey)
				.buildAndExpand(resolvedModel)
				.toUri()

			webClient.post()
				.uri(uri)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(
					mapOf(
						"contents" to listOf(
							mapOf(
								"role" to "user",
								"parts" to listOf(
									mapOf("text" to prompt),
								),
							),
						),
						"generationConfig" to mapOf(
							"maxOutputTokens" to maxOutput,
							"temperature" to 0.7,
						),
					),
				)
				.retrieve()
				.awaitBody<Map<String, Any>>()
		} catch (ex: WebClientResponseException) {
			throw IllegalStateException(
				"Gemini API error ${ex.statusCode.value()} for model=$resolvedModel: " +
					ex.responseBodyAsString.take(400),
				ex,
			)
		}

		val blockReason = (response["promptFeedback"] as? Map<*, *>)?.get("blockReason")
		if (blockReason != null) {
			throw IllegalStateException("Gemini blocked the prompt (blockReason=$blockReason)")
		}

		val text = extractText(response)
		if (text.isEmpty()) {
			val finish = firstFinishReason(response)
			throw IllegalStateException(
				"Gemini returned an empty response for model=$resolvedModel" +
					(finish?.let { " (finishReason=$it)" } ?: ""),
			)
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

	private fun firstFinishReason(response: Map<String, Any>): String? {
		val candidates = response["candidates"] as? List<*> ?: return null
		val first = candidates.firstOrNull() as? Map<*, *> ?: return null
		return first["finishReason"]?.toString()
	}
}
