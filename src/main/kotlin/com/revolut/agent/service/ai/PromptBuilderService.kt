package com.revolut.agent.service.ai

import org.springframework.stereotype.Service

@Service
class PromptBuilderService {

	/** Prompt production : tip LinkedIn complet. */
	fun buildDailyTipPrompt(contentTopic: String): String {
		val topic = contentTopic.trim().ifBlank { "astuces tech" }

		return """
			You are a professional LinkedIn content writer.

			Write ONE concrete, actionable tip about: "$topic".

			Hard requirements:
			- LinkedIn post style (hook + value, easy to skim)
			- Professional yet accessible tone
			- Length between 600 and 1000 characters
			- No hashtags
			- No meta commentary — return ONLY the raw post text
			- Prefer French if the topic is written in French
		""".trimIndent()
	}

	/** Prompt test : très court pour consommer un minimum de tokens. */
	fun buildTestTipPrompt(contentTopic: String): String {
		val topic = contentTopic.trim().ifBlank { "tech" }
		return """
			Écris un tip LinkedIn ultra-court sur "$topic" (max 250 caractères).
			Texte brut uniquement, sans hashtags ni intro.
		""".trimIndent()
	}
}
