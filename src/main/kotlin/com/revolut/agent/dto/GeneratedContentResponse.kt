package com.revolut.agent.dto

import com.revolut.agent.model.LlmProvider
import java.time.Instant

data class GeneratedContentResponse(
	val content: String,
	val provider: LlmProvider,
	val generatedAt: Instant,
	val promptUsed: String,
)
