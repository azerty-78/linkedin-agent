package com.revolut.agent.service.ai

import com.revolut.agent.model.settings.LlmProvider

data class AiGenerationCommand(
	val apiKey: String,
	val systemPrompt: String,
	val userPrompt: String,
	val model: String,
	val maxOutputTokens: Int,
) {
	/** Single-blob fallback for SDKs that only accept one string. */
	val prompt: String
		get() =
			"""
			$systemPrompt

			---

			$userPrompt
			""".trimIndent()
}

interface AiContentGenerator {

	val provider: LlmProvider

	suspend fun generate(command: AiGenerationCommand): String
}
