package com.revolut.agent.service.publication

import com.revolut.agent.dto.linkedin.LinkedInPublishBlockers
import com.revolut.agent.dto.publication.PublicationReadinessResponse
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.service.linkedin.LinkedInOAuthService
import com.revolut.agent.service.settings.OrganizationSettingsService
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class PublicationReadinessService(
	private val linkedInOAuthService: LinkedInOAuthService,
	private val organizationSettingsService: OrganizationSettingsService,
) {

	fun getReadiness(): Mono<PublicationReadinessResponse> =
		organizationSettingsService.getSettings()
			.zipWith(linkedInOAuthService.getSummary())
			.map { tuple ->
				val settings = tuple.t1
				val summary = tuple.t2
				val reasons = mutableListOf<String>()

				val activeAppConfigured = summary.hasAppCredentials
				if (!activeAppConfigured) {
					reasons += ReadinessReasons.NO_ACTIVE_APP
				}

				val active = summary.activeAccount
				val linkedInConnected = active?.status == LinkedInConnectionStatus.CONNECTED
				val orgUrn = active?.linkedInOrganizationUrn
				val label = active?.let { it.organizationName ?: it.label }

				when {
					active == null ||
						summary.publishBlockers.contains(LinkedInPublishBlockers.NO_ACTIVE_ACCOUNT) ||
						summary.publishBlockers.contains(LinkedInPublishBlockers.NOT_CONNECTED) ->
						reasons += ReadinessReasons.LINKEDIN_NOT_CONNECTED
					summary.publishBlockers.contains(LinkedInPublishBlockers.EXPIRED) ||
						summary.publishBlockers.contains(LinkedInPublishBlockers.ERROR_STATUS) ->
						reasons += ReadinessReasons.TOKEN_EXPIRED
				}
				if (
					summary.publishBlockers.contains(LinkedInPublishBlockers.MISSING_ORG_URN) ||
					(linkedInConnected && orgUrn.isNullOrBlank())
				) {
					reasons += ReadinessReasons.ORGANIZATION_URN_MISSING
				}

				val hasLlmKey = when (settings.activeLlmProvider) {
					LlmProvider.OPENAI -> settings.openAiKeyMasked.isNotBlank()
					LlmProvider.ANTHROPIC -> settings.anthropicKeyMasked.isNotBlank()
					LlmProvider.GEMINI -> settings.geminiKeyMasked.isNotBlank()
				}
				if (!hasLlmKey) {
					reasons += ReadinessReasons.NO_LLM_KEY
				}

				val distinctReasons = reasons.distinct()
				PublicationReadinessResponse(
					ready = distinctReasons.isEmpty(),
					linkedInConnected = linkedInConnected,
					linkedInAccountLabel = label?.takeIf { it.isNotBlank() },
					organizationUrn = orgUrn,
					activeAppConfigured = activeAppConfigured,
					hasLlmKey = hasLlmKey,
					reasons = distinctReasons,
				)
			}
}

object ReadinessReasons {
	const val LINKEDIN_NOT_CONNECTED = "LINKEDIN_NOT_CONNECTED"
	const val ORGANIZATION_URN_MISSING = "ORGANIZATION_URN_MISSING"
	const val NO_ACTIVE_APP = "NO_ACTIVE_APP"
	const val NO_LLM_KEY = "NO_LLM_KEY"
	const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
}
