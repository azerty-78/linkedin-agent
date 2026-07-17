package com.revolut.agent.service.ai

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.model.settings.LlmProvider
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiContentGenerator(
	private val aiModelProperties: AiModelProperties,
) : AiContentGenerator {

	override val provider: LlmProvider = LlmProvider.OPENAI

	override suspend fun generate(apiKey: String, prompt: String, model: String): String {
		val economicalModel = economicalOpenAiModel(model)
		val options = OpenAiChatOptions.builder()
			.apiKey(apiKey)
			.model(economicalModel)
			.maxTokens(aiModelProperties.maxOutputTokens)
			.timeout(Duration.ofSeconds(30))
			.build()

		val chatModel = OpenAiChatModel.builder()
			.options(options)
			.build()

		return chatModel.call(prompt)?.trim().orEmpty()
	}

	/**
	 * Force un modèle OpenAI bas coût même si l'UI envoie gpt-4o / sonnet-équivalents.
	 */
	private fun economicalOpenAiModel(requested: String): String {
		val id = requested.trim().lowercase()
		return when {
			id.contains("nano") -> requested.trim().ifBlank { aiModelProperties.openai.model }
			id.contains("mini") -> requested.trim()
			else -> aiModelProperties.openai.model
		}
	}
}
