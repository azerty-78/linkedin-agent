package com.revolut.agent.dto.dashboard

import com.revolut.agent.dto.publication.PublicationReadinessResponse
import com.revolut.agent.dto.publication.PublicationResponse
import com.revolut.agent.model.settings.LlmProvider
import java.time.Instant

data class DashboardPublicationCounts(
	val total: Long,
	val success: Long,
	val failure: Long,
	val awaitingReview: Long,
	val scheduled: Long,
	val inProgress: Long,
)

data class DashboardLinkedInSummary(
	val connected: Boolean,
	val accountsCount: Int,
	val activeAccountsCount: Int,
	val appsCount: Int,
	val activeAppsCount: Int,
	val maxApps: Int,
	val activeLabel: String?,
)

data class DashboardSettingsSummary(
	val companyName: String,
	val schedulerEnabled: Boolean,
	val publishHourUtc: Int,
	val timezone: String,
	val activeLlmProvider: LlmProvider,
	val hasAnyLlmKey: Boolean,
	val configuredProvidersCount: Int,
	val configuredProviders: List<LlmProvider>,
)

data class DashboardOverviewResponse(
	val fromBackend: Boolean = true,
	val counts: DashboardPublicationCounts,
	val nextScheduledAt: Instant?,
	val nextOneShotAt: Instant?,
	val lastPublication: PublicationResponse?,
	val recentPublications: List<PublicationResponse>,
	val readiness: PublicationReadinessResponse,
	val linkedIn: DashboardLinkedInSummary,
	val settingsSummary: DashboardSettingsSummary,
)
