package com.revolut.agent.dto.settings
import com.revolut.agent.model.settings.LlmProvider
import java.time.Instant

data class OrganizationSettingsResponse(
	val companyName: String,
	val contentTopic: String,
	val publishHourUtc: Int,
	val timezone: String,
	val activeLlmProvider: LlmProvider,
	val openAiKeyMasked: String,
	val anthropicKeyMasked: String,
	val geminiKeyMasked: String,
	val linkedInOrganizationId: String?,
	val schedulerEnabled: Boolean,
	val updatedAt: Instant,
)
