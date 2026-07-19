package com.revolut.agent.service.dashboard

import com.revolut.agent.dto.dashboard.DashboardLinkedInSummary
import com.revolut.agent.dto.dashboard.DashboardOverviewResponse
import com.revolut.agent.dto.dashboard.DashboardPublicationCounts
import com.revolut.agent.dto.dashboard.DashboardSettingsSummary
import com.revolut.agent.dto.publication.PublicationResponse
import com.revolut.agent.dto.publication.PublicationStepResponse
import com.revolut.agent.model.linkedin.LinkedInApp
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.StepName
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.repository.publication.PublicationRepository
import com.revolut.agent.service.linkedin.LinkedInAppService
import com.revolut.agent.service.linkedin.LinkedInOAuthService
import com.revolut.agent.service.publication.PublicationReadinessService
import com.revolut.agent.service.publication.SchedulerDecision
import com.revolut.agent.service.settings.OrganizationSettingsService
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.Optional

@Service
class DashboardOverviewService(
	private val publicationRepository: PublicationRepository,
	private val organizationSettingsService: OrganizationSettingsService,
	private val publicationReadinessService: PublicationReadinessService,
	private val linkedInOAuthService: LinkedInOAuthService,
	private val linkedInAppService: LinkedInAppService,
) {

	fun getOverview(): Mono<DashboardOverviewResponse> =
		Mono.zip(
			loadCounts(),
			organizationSettingsService.getSettings(),
			publicationReadinessService.getReadiness(),
			linkedInOAuthService.getSummary(),
			linkedInAppService.listApps(),
			loadRecent(),
			loadNextOneShot(),
		).map { tuple ->
			val counts = tuple.t1
			val settings = tuple.t2
			val readiness = tuple.t3
			val liSummary = tuple.t4
			val apps = tuple.t5
			val recent = tuple.t6
			val nextOneShot = tuple.t7.orElse(null)

			val configured = buildList {
				if (settings.geminiKeyMasked.isNotBlank()) add(LlmProvider.GEMINI)
				if (settings.openAiKeyMasked.isNotBlank()) add(LlmProvider.OPENAI)
				if (settings.anthropicKeyMasked.isNotBlank()) add(LlmProvider.ANTHROPIC)
			}

			val activeAccount = liSummary.activeAccount
			val connected = activeAccount?.status == LinkedInConnectionStatus.CONNECTED
			val activeLabel = activeAccount?.let { it.organizationName ?: it.label }?.takeIf { it.isNotBlank() }

			DashboardOverviewResponse(
				fromBackend = true,
				counts = counts,
				nextScheduledAt = SchedulerDecision.computeNextScheduledAt(settings),
				nextOneShotAt = nextOneShot,
				lastPublication = recent.firstOrNull(),
				recentPublications = recent,
				readiness = readiness,
				linkedIn = DashboardLinkedInSummary(
					connected = connected,
					accountsCount = liSummary.accountsCount,
					activeAccountsCount = if (activeAccount != null) 1 else 0,
					appsCount = apps.apps.size,
					activeAppsCount = apps.apps.count { it.isActive },
					maxApps = apps.maxApps.coerceAtLeast(LinkedInApp.MAX_APPS),
					activeLabel = activeLabel,
				),
				settingsSummary = DashboardSettingsSummary(
					companyName = settings.companyName,
					schedulerEnabled = settings.schedulerEnabled,
					publishHourUtc = settings.publishHourUtc,
					timezone = settings.timezone,
					activeLlmProvider = settings.activeLlmProvider,
					hasAnyLlmKey = configured.isNotEmpty(),
					configuredProvidersCount = configured.size,
					configuredProviders = configured,
				),
			)
		}

	private fun loadCounts(): Mono<DashboardPublicationCounts> =
		Mono.zip(
			publicationRepository.count(),
			publicationRepository.countByStatus(PublicationStatus.SUCCESS),
			publicationRepository.countByStatus(PublicationStatus.PARTIAL_SUCCESS),
			publicationRepository.countByStatus(PublicationStatus.FAILURE),
			publicationRepository.countByStatus(PublicationStatus.AWAITING_REVIEW),
			publicationRepository.countByStatus(PublicationStatus.SCHEDULED),
			publicationRepository.countByStatus(PublicationStatus.IN_PROGRESS),
		).map { t ->
			DashboardPublicationCounts(
				total = t.t1,
				success = t.t2 + t.t3,
				failure = t.t4,
				awaitingReview = t.t5,
				scheduled = t.t6,
				inProgress = t.t7,
			)
		}

	private fun loadRecent(): Mono<List<PublicationResponse>> =
		publicationRepository.findTop5ByOrderByTriggeredAtDesc()
			.map(::toResponse)
			.collectList()

	private fun loadNextOneShot(): Mono<Optional<Instant>> =
		publicationRepository
			.findFirstByStatusAndScheduledPublishAtGreaterThanEqualOrderByScheduledPublishAtAsc(
				PublicationStatus.SCHEDULED,
				Instant.now(),
			)
			.map { pub -> Optional.ofNullable(pub.scheduledPublishAt) }
			.defaultIfEmpty(Optional.empty())

	private fun toResponse(publication: Publication): PublicationResponse {
		val draft = publication.steps
			.firstOrNull { it.stepName == StepName.CONTENT_GENERATION }
			?.output
		return PublicationResponse(
			id = publication.id,
			triggerType = publication.triggerType,
			status = publication.status,
			triggeredAt = publication.triggeredAt,
			completedAt = publication.completedAt,
			steps = publication.steps.map { step ->
				PublicationStepResponse(
					stepName = step.stepName,
					status = step.status,
					startedAt = step.startedAt,
					completedAt = step.completedAt,
					errorMessage = step.errorMessage,
					output = step.output,
				)
			},
			triggeredBy = publication.triggeredBy,
			draftContent = draft,
			llmProvider = publication.llmProvider,
			scheduledPublishAt = publication.scheduledPublishAt,
		)
	}
}
