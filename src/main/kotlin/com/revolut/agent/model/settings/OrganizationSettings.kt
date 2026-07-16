package com.revolut.agent.model.settings
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "organization_settings")
data class OrganizationSettings(
	@Id
	val id: String? = null,
	val companyName: String = "",
	val contentTopic: String = "",
	val publishHourUtc: Int = 9,
	val timezone: String = "Africa/Douala",
	val activeLlmProvider: LlmProvider = LlmProvider.ANTHROPIC,
	val encryptedOpenAiKey: String? = null,
	val encryptedAnthropicKey: String? = null,
	val encryptedGeminiKey: String? = null,
	val linkedInOrganizationId: String? = null,
	val schedulerEnabled: Boolean = false,
	val updatedAt: Instant = Instant.now(),
	val updatedBy: String? = null,
)
