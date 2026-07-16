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

	val openAiApiKey: String? = null,
	val anthropicApiKey: String? = null,
	val geminiApiKey: String? = null,
	val linkedInOrganizationId: String? = null,
)
