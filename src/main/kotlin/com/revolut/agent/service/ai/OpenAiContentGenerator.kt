package com.revolut.agent.service.ai

import com.revolut.agent.model.settings.LlmProvider
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiContentGenerator : AiContentGenerator {

	override val provider: LlmProvider = LlmProvider.OPENAI

	override suspend fun generate(command: AiGenerationCommand): String {
		val options = OpenAiChatOptions.builder()
			.apiKey(command.apiKey)
			.model(command.model)
			.maxTokens(command.maxOutputTokens)
			.timeout(Duration.ofSeconds(30))
			.build()

		val chatModel = OpenAiChatModel.builder()
			.options(options)
			.build()

		return chatModel.call(command.prompt)?.trim().orEmpty()
	}
}
