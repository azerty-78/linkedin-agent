package com.revolut.agent.service.ai

import org.springframework.stereotype.Service

@Service
class PromptBuilderService {

	fun buildDailyTipPrompt(contentTopic: String): String {
		val topic = contentTopic.trim().ifBlank { "astuces informatiques pour développeurs" }

		return """
			You are a professional LinkedIn content writer specializing in practical tech tips.

			Write ONE concrete, actionable tip about: "$topic".

			Hard requirements:
			- Suitable as a LinkedIn post (engaging hook, clear value, easy to skim)
			- Professional yet accessible tone; avoid excessive jargon
			- Length between 800 and 1200 characters
			- Do NOT include hashtags in the text
			- Do NOT include meta commentary (no "As an AI", no "Here's your tip", no preamble or closing apology)
			- Return ONLY the raw post text, nothing else
			- Prefer French if the topic is written in French; otherwise match the topic language
		""".trimIndent()
	}
}
