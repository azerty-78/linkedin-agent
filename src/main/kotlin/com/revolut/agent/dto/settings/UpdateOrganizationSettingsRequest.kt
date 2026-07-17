package com.revolut.agent.dto.settings

import com.revolut.agent.model.settings.LlmProvider
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class UpdateOrganizationSettingsRequest(
	@field:NotBlank
	val companyName: String,

	@field:NotBlank
	val contentTopic: String,

	@field:Min(0)
	@field:Max(23)
	val publishHourUtc: Int,

	@field:NotBlank
	val timezone: String,

	val activeLlmProvider: LlmProvider,

	/** Modèle OpenAI (ex. gpt-4o-mini). Null = conserver la valeur existante. */
	val openAiModel: String? = null,
	/** Modèle Anthropic (ex. claude-haiku-4-5). Null = conserver. */
	val anthropicModel: String? = null,
	/** Modèle Gemini (ex. gemini-3.5-flash). Null = conserver. */
	val geminiModel: String? = null,

	val openAiApiKey: String? = null,
	val anthropicApiKey: String? = null,
	val geminiApiKey: String? = null,
	val linkedInOrganizationId: String? = null,
	/** Active le scheduler de publication quotidienne. Null = conserver. */
	val schedulerEnabled: Boolean? = null,
)
