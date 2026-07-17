package com.revolut.agent.dto.settings

import com.revolut.agent.model.settings.LlmProvider
import java.time.Instant

data class OrganizationSettingsResponse(
	val companyName: String,
	val contentTopic: String,
	val publishHourUtc: Int,
	val timezone: String,
	val activeLlmProvider: LlmProvider,
	val openAiModel: String,
	val anthropicModel: String,
	val geminiModel: String,
	val openAiKeyMasked: String,
	val anthropicKeyMasked: String,
	val geminiKeyMasked: String,
	val linkedInOrganizationId: String?,
	/** Client ID app LinkedIn (public). */
	val linkedInClientId: String?,
	val linkedInClientSecretMasked: String,
	val linkedInRedirectUri: String?,
	val linkedInOauthScopes: String?,
	/** Id du compte LinkedIn actif (parmi max 3). */
	val activeLinkedInAccountId: String?,
	val schedulerEnabled: Boolean,
	val updatedAt: Instant,
)
