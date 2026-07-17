package com.revolut.agent.service.ai

import com.revolut.agent.model.settings.LlmProvider

interface AiContentGenerator {

	val provider: LlmProvider

	suspend fun generate(apiKey: String, prompt: String, model: String): String
}
