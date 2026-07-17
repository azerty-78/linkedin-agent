package com.revolut.agent.dto.ai

import com.revolut.agent.model.settings.LlmProvider
import java.time.Instant

data class GeneratedContentResponse(
	val content: String,
	val provider: LlmProvider,
	val model: String,
	val generatedAt: Instant,
	val promptUsed: String,
)
