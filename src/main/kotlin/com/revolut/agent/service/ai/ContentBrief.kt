package com.revolut.agent.service.ai

import com.revolut.agent.model.settings.ContentFormat
import com.revolut.agent.model.settings.LlmProvider

data class TargetLength(
	val min: Int,
	val max: Int,
)

data class ContentBrief(
	val systemPrompt: String,
	val userPrompt: String,
	val language: String,
	val formatChosen: ContentFormat?,
	val targetLength: TargetLength,
	val hashtags: List<String>,
	val provider: LlmProvider,
	val model: String,
	val emojiPolicyName: String,
) {
	/** Combined prompt for generators that only accept a single string. */
	fun combinedPrompt(): String =
		"""
		$systemPrompt

		---

		$userPrompt
		""".trimIndent()
}
