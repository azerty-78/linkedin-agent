package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai")
class AiModelProperties {

	var openai: OpenAi = OpenAi()
	var anthropic: Anthropic = Anthropic()
	var gemini: Gemini = Gemini()
	/** Cap de sortie commun (tips courts) pour limiter coût / quota. */
	var maxOutputTokens: Int = 180

	class OpenAi {
		/** Plus petit modèle OpenAI recommandé (pas gratuit — nécessite crédit OpenAI). */
		var model: String = "gpt-4.1-nano"
	}

	class Anthropic {
		var model: String = "claude-haiku-4-5"
		var maxTokens: Int = 180
	}

	class Gemini {
		var model: String = "gemini-2.0-flash"
		var apiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
		var maxOutputTokens: Int = 180
	}
}
