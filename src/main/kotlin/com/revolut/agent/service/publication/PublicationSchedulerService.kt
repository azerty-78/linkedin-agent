package com.revolut.agent.service.publication

import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.repository.publication.PublicationRepository
import com.revolut.agent.service.linkedin.LinkedInOAuthService
import com.revolut.agent.service.settings.OrganizationSettingsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference

@Service
class PublicationSchedulerService(
	private val organizationSettingsService: OrganizationSettingsService,
	private val publicationOrchestratorService: PublicationOrchestratorService,
	private val publicationRepository: PublicationRepository,
	private val linkedInOAuthService: LinkedInOAuthService,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	/** Garde anti double-fire dans la fenêtre ±5 min (complète l’idempotence DB SUCCESS). */
	private val lastScheduledTriggerDate: AtomicReference<LocalDate?> = AtomicReference(null)

	fun lastScheduledRunDate(): LocalDate? = lastScheduledTriggerDate.get()

	/**
	 * Tick toutes les 5 min. Fenêtre : `publishHourUtc:00` UTC ± 5 min.
	 * Jours / idempotence : calendrier de `timezone` org.
	 */
	@Scheduled(fixedRate = 300_000)
	fun checkAndTriggerScheduledPublication() {
		try {
			organizationSettingsService.getSettings()
				.flatMap { settings -> evaluateAndMaybeTrigger(settings) }
				.doOnError { ex ->
					log.error("Scheduler check failed (will retry next cycle): {}", ex.message)
				}
				.onErrorResume { Mono.empty() }
				.block()
		} catch (ex: Exception) {
			log.error("Scheduler check crashed (isolated): {}", ex.message, ex)
		}
	}

	internal fun evaluateAndMaybeTrigger(settings: OrganizationSettingsResponse): Mono<Void> {
		if (!settings.schedulerEnabled) {
			log.debug("Scheduler check skipped | schedulerEnabled=false")
			return Mono.empty()
		}

		return warnIfMisconfigured(settings)
			.then(
				Mono.defer {
					val nowUtc = ZonedDateTime.now(ZoneOffset.UTC)
					if (!SchedulerDecision.isWithinPublishWindow(nowUtc, settings.publishHourUtc)) {
						log.debug(
							"Scheduler check | outside window | hourUtc={} | now={}",
							settings.publishHourUtc,
							nowUtc.toLocalTime(),
						)
						return@defer Mono.empty()
					}

					val freq = SchedulerDecision.evaluateFrequency(settings, nowUtc)
					if (!freq.shouldRun) {
						log.debug(
							"Scheduler check | frequency skip | freq={} | days={}",
							settings.schedulerFrequency,
							settings.schedulerDaysOfWeek,
						)
						return@defer Mono.empty()
					}

					val zone = SchedulerDecision.resolveZone(settings.timezone)
					val todayLocal = nowUtc.withZoneSameInstant(zone).toLocalDate()
					val dayStart = todayLocal.atStartOfDay(zone).toInstant()
					val dayEnd = todayLocal.plusDays(1).atStartOfDay(zone).toInstant()

					val persistAnchor: Mono<OrganizationSettingsResponse> =
						if (freq.anchorToPersist != null) {
							organizationSettingsService.ensureSchedulerAnchorDate(freq.anchorToPersist)
						} else {
							Mono.just(settings)
						}

					persistAnchor.flatMap { _ ->
						if (lastScheduledTriggerDate.get() == todayLocal) {
							log.debug("Scheduler check | already triggered today (memory) {}", todayLocal)
							return@flatMap Mono.empty()
						}
						publicationRepository
							.existsByTriggerTypeAndStatusInAndTriggeredAtGreaterThanEqualAndTriggeredAtLessThan(
								triggerType = PublicationTriggerType.SCHEDULED,
								statuses = listOf(PublicationStatus.SUCCESS, PublicationStatus.PARTIAL_SUCCESS),
								from = dayStart,
								to = dayEnd,
							)
							.flatMap { alreadySuccess ->
								if (alreadySuccess) {
									log.debug("Scheduler check | already SUCCESS today {}", todayLocal)
									lastScheduledTriggerDate.set(todayLocal)
									Mono.empty()
								} else {
									publicationRepository.existsByStatus(PublicationStatus.IN_PROGRESS)
										.flatMap { running ->
											if (running) {
												log.warn("Scheduler check | skipped — publication already IN_PROGRESS")
												Mono.empty()
											} else {
												lastScheduledTriggerDate.set(todayLocal)
												log.info(
													"Scheduler triggering publication | localDate={} | tz={} | freq={} | publishHourUtc={}",
													todayLocal,
													settings.timezone,
													settings.schedulerFrequency,
													settings.publishHourUtc,
												)
												publicationOrchestratorService.orchestrate(
													triggerType = PublicationTriggerType.SCHEDULED,
													triggeredBy = "scheduler",
												).then()
											}
										}
								}
							}
					}
				},
			)
	}

	private fun warnIfMisconfigured(settings: OrganizationSettingsResponse): Mono<Void> {
		val keyMissing = when (settings.activeLlmProvider) {
			LlmProvider.OPENAI -> settings.openAiKeyMasked.isBlank()
			LlmProvider.ANTHROPIC -> settings.anthropicKeyMasked.isBlank()
			LlmProvider.GEMINI -> settings.geminiKeyMasked.isBlank()
		}
		if (keyMissing) {
			log.warn(
				"Scheduler enabled but no API key configured for active provider {} — scheduled runs will fail at CONTENT_GENERATION",
				settings.activeLlmProvider,
			)
		}
		if (settings.linkedInClientId.isNullOrBlank() || settings.linkedInClientSecretMasked.isBlank()) {
			log.warn("Scheduler enabled but LinkedIn app credentials missing in /api/settings")
		}
		return linkedInOAuthService.resolveActiveAccount()
			.map { it.status != LinkedInConnectionStatus.CONNECTED }
			.defaultIfEmpty(true)
			.doOnNext { notConnected ->
				if (notConnected) {
					log.warn(
						"Scheduler enabled but no active LinkedIn account CONNECTED — scheduled runs will fail at LINKEDIN_PUBLISH",
					)
				}
			}
			.then()
	}
}
