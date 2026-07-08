package com.revolut.agent.service.ai

import com.revolut.agent.model.LlmProvider

interface AiContentGenerator {

	val provider: LlmProvider

	suspend fun generate(apiKey: String, prompt: String): String
}
