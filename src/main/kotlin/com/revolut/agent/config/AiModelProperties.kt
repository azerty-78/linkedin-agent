package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai")
class AiModelProperties {

	var openai: OpenAi = OpenAi()
	var anthropic: Anthropic = Anthropic()

	class OpenAi {
		var model: String = "gpt-4o-mini"
	}

	class Anthropic {
		var model: String = "claude-sonnet-4-6"
		var maxTokens: Int = 2048
	}
}
