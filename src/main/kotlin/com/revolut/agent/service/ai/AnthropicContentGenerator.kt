package com.revolut.agent.service.ai

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.model.LlmProvider
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AnthropicContentGenerator(
	private val aiModelProperties: AiModelProperties,
) : AiContentGenerator {

	override val provider: LlmProvider = LlmProvider.ANTHROPIC

	override suspend fun generate(apiKey: String, prompt: String): String {
		val options = AnthropicChatOptions.builder()
			.apiKey(apiKey)
			.model(aiModelProperties.anthropic.model)
			.maxTokens(aiModelProperties.anthropic.maxTokens)
			.timeout(Duration.ofSeconds(30))
			.build()

		val chatModel = AnthropicChatModel.builder()
			.options(options)
			.build()

		return chatModel.call(prompt).trim()
	}
}
