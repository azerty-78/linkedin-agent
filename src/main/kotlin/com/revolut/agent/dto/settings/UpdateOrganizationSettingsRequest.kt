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

	val openAiModel: String? = null,
	val anthropicModel: String? = null,
	val geminiModel: String? = null,

	val openAiApiKey: String? = null,
	val anthropicApiKey: String? = null,
	val geminiApiKey: String? = null,
	val linkedInOrganizationId: String? = null,

	/** Client ID LinkedIn Developer App. Null = conserver. */
	val linkedInClientId: String? = null,
	/** Client secret — null/blank = conserver. */
	val linkedInClientSecret: String? = null,
	val linkedInRedirectUri: String? = null,
	val linkedInOauthScopes: String? = null,
	/** Compte LinkedIn à utiliser pour publier. Null = conserver. */
	val activeLinkedInAccountId: String? = null,

	val schedulerEnabled: Boolean? = null,
)
