package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai")
class AiModelProperties {

	var openai: OpenAi = OpenAi()
	var anthropic: Anthropic = Anthropic()
	var gemini: Gemini = Gemini()

	class OpenAi {
		/** Défaut économique pour les tests. */
		var model: String = "gpt-4o-mini"
	}

	class Anthropic {
		/** Haiku = tier économique (évite Sonnet pour les tests). */
		var model: String = "claude-haiku-4-5"
		var maxTokens: Int = 2048
	}

	class Gemini {
		/** Free tier Google AI Studio — recommandé pour les tests. */
		var model: String = "gemini-2.0-flash"
		var apiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
	}
}
