package com.revolut.agent.service.ai

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.model.settings.LlmModelCatalog
import com.revolut.agent.model.settings.LlmProvider
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import java.net.URI

/**
 * Appel direct à l'API Google Generative Language (free tier AI Studio).
 */
@Component
class GeminiContentGenerator(
	private val aiModelProperties: AiModelProperties,
	webClientBuilder: WebClient.Builder,
) : AiContentGenerator {

	override val provider: LlmProvider = LlmProvider.GEMINI

	/** Builder cloné sans baseUrl locale — on passe toujours une URL absolue https. */
	private val webClient: WebClient = webClientBuilder.clone().build()

	override suspend fun generate(command: AiGenerationCommand): String {
		val resolvedModel = LlmModelCatalog.resolveGeminiModel(command.model)
		val baseUrl = aiModelProperties.gemini.apiBaseUrl.trimEnd('/')
			.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }

		require(baseUrl.startsWith("https://")) {
			"ai.gemini.api-base-url must be an https URL, got: $baseUrl"
		}

		// URL absolue obligatoire — évite Connection refused sur localhost:80
		val endpoint = URI.create("$baseUrl/models/$resolvedModel:generateContent")

		val response = try {
			webClient.post()
				.uri(endpoint)
				.header("x-goog-api-key", command.apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(
					mapOf(
						"contents" to listOf(
							mapOf(
								"role" to "user",
								"parts" to listOf(
									mapOf("text" to command.prompt),
								),
							),
						),
						"generationConfig" to mapOf(
							"maxOutputTokens" to command.maxOutputTokens,
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
