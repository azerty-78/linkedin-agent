package com.revolut.agent.service.ai

import org.springframework.stereotype.Service

@Service
class PromptBuilderService {

	/**
	 * Prompt volontairement court pour limiter tokens d'entrée + sortie (~tip 300–500 car.).
	 */
	fun buildDailyTipPrompt(contentTopic: String): String {
		val topic = contentTopic.trim().ifBlank { "astuces tech" }

		return """
			Rédige UN tip LinkedIn sur: "$topic".
			Règles: 300-500 caractères, ton pro, français si le sujet est en français.
			Sans hashtags, sans intro/meta ("Voici un tip…"). Texte brut uniquement.
		""".trimIndent()
	}
}
