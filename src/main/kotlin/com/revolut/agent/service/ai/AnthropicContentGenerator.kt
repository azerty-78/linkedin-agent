package com.revolut.agent.service.ai

import com.anthropic.models.messages.Model
import com.revolut.agent.model.settings.LlmProvider
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AnthropicContentGenerator : AiContentGenerator {

	override val provider: LlmProvider = LlmProvider.ANTHROPIC

	override suspend fun generate(command: AiGenerationCommand): String {
		val options = AnthropicChatOptions.builder()
			.apiKey(command.apiKey)
			.model(Model.of(command.model))
			.maxTokens(command.maxOutputTokens)
			.timeout(Duration.ofSeconds(30))
			.build()

		val chatModel = AnthropicChatModel.builder()
			.options(options)
			.build()

		return chatModel.call(command.prompt)?.trim().orEmpty()
	}
}
