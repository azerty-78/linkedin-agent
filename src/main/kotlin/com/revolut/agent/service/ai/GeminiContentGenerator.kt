package com.revolut.agent.service.ai

import com.revolut.agent.model.settings.LlmProvider
import org.springframework.stereotype.Component

@Component
class GeminiContentGenerator : AiContentGenerator {

	override val provider: LlmProvider = LlmProvider.GEMINI

	override suspend fun generate(apiKey: String, prompt: String): String {
		throw UnsupportedOperationException(
			"Gemini provider not yet implemented — add spring-ai-starter-model-google-genai dependency first",
		)
	}
}
