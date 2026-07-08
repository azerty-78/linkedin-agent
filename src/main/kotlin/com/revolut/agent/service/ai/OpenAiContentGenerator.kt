package com.revolut.agent.service.ai

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.model.LlmProvider
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiContentGenerator(
	private val aiModelProperties: AiModelProperties,
) : AiContentGenerator {

	override val provider: LlmProvider = LlmProvider.OPENAI

	override suspend fun generate(apiKey: String, prompt: String): String {
		val options = OpenAiChatOptions.builder()
			.apiKey(apiKey)
			.model(aiModelProperties.openai.model)
			.timeout(Duration.ofSeconds(30))
			.build()

		val chatModel = OpenAiChatModel.builder()
			.options(options)
			.build()

		return chatModel.call(prompt).trim()
	}
}
