package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Config AI technique.
 * Clés API + modèles de prod = OrganizationSettings (front / PUT /api/settings).
 * Seul [test] est destiné à être surchargé dans application.properties.
 */
@ConfigurationProperties(prefix = "ai")
class AiModelProperties {

	var openai: OpenAi = OpenAi()
	var anthropic: Anthropic = Anthropic()
	var gemini: Gemini = Gemini()
	/** Cap sortie génération réelle (scheduler) — pas un choix UI. */
	var maxOutputTokens: Int = 1024
	/** Overrides dédiés à POST /api/ai/generate-test uniquement. */
	var test: Test = Test()

	class OpenAi {
		/** Réservé — préférer [com.revolut.agent.model.settings.LlmModelCatalog]. */
		var model: String = ""
	}

	class Anthropic {
		var model: String = ""
		var maxTokens: Int = 1024
	}

	class Gemini {
		var model: String = ""
		var apiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
		var maxOutputTokens: Int = 1024
	}

	/**
	 * Modèles faibles / free pour les tests manuels uniquement.
	 * Indépendants des modèles choisis en settings.
	 */
	class Test {
		var maxOutputTokens: Int = 80
		var openaiModel: String = "gpt-4.1-nano"
		var anthropicModel: String = "claude-haiku-4-5"
		var geminiModel: String = "gemini-3.1-flash-lite"
		/** Si une clé Gemini existe, les tests l’utilisent en priorité (gratuit). */
		var preferGemini: Boolean = true
	}
}
