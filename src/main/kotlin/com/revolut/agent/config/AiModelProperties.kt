package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai")
class AiModelProperties {

	var openai: OpenAi = OpenAi()
	var anthropic: Anthropic = Anthropic()
	var gemini: Gemini = Gemini()
	/** Cap sortie pour la génération réelle (scheduler / prod). */
	var maxOutputTokens: Int = 1024
	/** Overrides dédiés à POST /api/ai/generate-test uniquement. */
	var test: Test = Test()

	class OpenAi {
		/** Fallback si l’org n’a pas encore choisi de modèle. */
		var model: String = "gpt-4o-mini"
	}

	class Anthropic {
		var model: String = "claude-haiku-4-5"
		var maxTokens: Int = 1024
	}

	class Gemini {
		var model: String = "gemini-3.5-flash"
		var apiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
		var maxOutputTokens: Int = 1024
	}

	/**
	 * Modèles volontairement faibles / free pour les tests manuels.
	 * Ne pas confondre avec les choix utilisateur en settings.
	 */
	class Test {
		var maxOutputTokens: Int = 80
		var openaiModel: String = "gpt-4.1-nano"
		var anthropicModel: String = "claude-haiku-4-5"
		/** Plus léger du free tier Gemini. */
		var geminiModel: String = "gemini-2.5-flash-lite"
		/** Si une clé Gemini existe, les tests l’utilisent en priorité (gratuit). */
		var preferGemini: Boolean = true
	}
}
