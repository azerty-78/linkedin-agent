package com.revolut.agent.dto.settings

import com.revolut.agent.model.settings.LlmProvider

data class LlmModelOptionResponse(
	val id: String,
	val label: String,
	val provider: LlmProvider,
	val freeTier: Boolean,
	val recommendedForTests: Boolean,
)

data class LlmModelsCatalogResponse(
	val models: List<LlmModelOptionResponse>,
	val defaultByProvider: Map<LlmProvider, String>,
	val recommendedTestByProvider: Map<LlmProvider, String>,
)
