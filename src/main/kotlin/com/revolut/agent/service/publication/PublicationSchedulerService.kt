package com.revolut.agent.service.publication

import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.model.linkedin.LinkedInConnection
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.publication.PublicationRepository
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
	private val linkedInConnectionRepository: LinkedInConnectionRepository,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	/** Dernière date UTC où un run SCHEDULED a été déclenché (évite doublon le même jour). */
	private val lastScheduledTriggerDate: AtomicReference<LocalDate?> = AtomicReference(null)

	fun lastScheduledRunDate(): LocalDate? = lastScheduledTriggerDate.get()

	/**
	 * Tolérance ±5 min autour de `publishHourUtc:00` UTC.
	 * Ex. publishHourUtc=15 → fenêtre [14:55, 15:05] UTC.
	 *
	 * Edge case restart pendant la fenêtre : [lastScheduledTriggerDate] est en mémoire JVM.
	 * Après restart la date est perdue → un 2e trigger le même jour est possible uniquement
	 * si le check retombe encore dans la fenêtre (±5 min). Hors fenêtre = pas de doublon.
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

	private fun evaluateAndMaybeTrigger(settings: OrganizationSettingsResponse): Mono<Void> {
		if (!settings.schedulerEnabled) {
			log.debug("Scheduler check skipped | schedulerEnabled=false")
			return Mono.empty()
		}

		return warnIfMisconfigured(settings)
			.then(
				Mono.defer {
					val nowUtc = ZonedDateTime.now(ZoneOffset.UTC)
					val today = nowUtc.toLocalDate()
					if (!isWithinPublishWindow(nowUtc, settings.publishHourUtc)) {
						log.debug(
							"Scheduler check | outside window | hourUtc={} | now={}",
							settings.publishHourUtc,
							nowUtc.toLocalTime(),
						)
						return@defer Mono.empty()
					}
					if (lastScheduledTriggerDate.get() == today) {
						log.debug("Scheduler check | already triggered today {}", today)
						return@defer Mono.empty()
					}
					publicationRepository.existsByStatus(PublicationStatus.IN_PROGRESS)
						.flatMap { running ->
							if (running) {
								log.warn("Scheduler check | skipped — publication already IN_PROGRESS")
								Mono.empty()
							} else {
								lastScheduledTriggerDate.set(today)
								log.info(
									"Scheduler triggering publication | date={} | publishHourUtc={}",
									today,
									settings.publishHourUtc,
								)
								publicationOrchestratorService.orchestrate(
									triggerType = PublicationTriggerType.SCHEDULED,
									triggeredBy = "scheduler",
								).then()
							}
						}
				},
			)
	}

	/**
	 * Fenêtre [target - 5min, target + 5min] où target = aujourd'hui à publishHourUtc:00:00 UTC.
	 */
	internal fun isWithinPublishWindow(nowUtc: ZonedDateTime, publishHourUtc: Int): Boolean {
		val hour = publishHourUtc.coerceIn(0, 23)
		val target = nowUtc.toLocalDate().atTime(hour, 0).atZone(ZoneOffset.UTC)
		val start = target.minusMinutes(WINDOW_MINUTES)
		val end = target.plusMinutes(WINDOW_MINUTES)
		return !nowUtc.isBefore(start) && !nowUtc.isAfter(end)
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
		return linkedInConnectionRepository.findById(LinkedInConnection.SINGLETON_ID)
			.map { it.status != LinkedInConnectionStatus.CONNECTED }
			.defaultIfEmpty(true)
			.doOnNext { notConnected ->
				if (notConnected) {
					log.warn(
						"Scheduler enabled but LinkedIn is not CONNECTED — scheduled runs will fail at LINKEDIN_PUBLISH",
					)
				}
			}
			.then()
	}

	companion object {
		private const val WINDOW_MINUTES = 5L
	}
}
