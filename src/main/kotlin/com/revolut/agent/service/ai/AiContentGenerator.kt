package com.revolut.agent.service.ai

import com.revolut.agent.model.settings.LlmProvider

data class AiGenerationCommand(
	val apiKey: String,
	val prompt: String,
	val model: String,
	val maxOutputTokens: Int,
)

interface AiContentGenerator {

	val provider: LlmProvider

	suspend fun generate(command: AiGenerationCommand): String
}
