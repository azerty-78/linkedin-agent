package com.revolut.agent.config.health

import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.service.linkedin.LinkedInOAuthService
import com.revolut.agent.service.publication.PublicationSchedulerService
import com.revolut.agent.service.settings.OrganizationSettingsService
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Indicateur **informatif** — un LinkedIn non connecté ne doit **pas** faire
 * échouer `/actuator/health` (sinon Docker/CI → 503 alors que l’API tourne).
 */
@Component("linkedin")
class LinkedInHealthIndicator(
	private val linkedInOAuthService: LinkedInOAuthService,
) : ReactiveHealthIndicator {

	override fun health(): Mono<Health> =
		linkedInOAuthService.resolveActiveAccount()
			.map { connection ->
				val builder = when (connection.status) {
					LinkedInConnectionStatus.CONNECTED -> Health.up()
					else -> Health.up() // app UP ; détail = état LinkedIn
				}
				builder
					.withDetail("status", connection.status.name)
					.withDetail("accountId", connection.id ?: "")
					.withDetail("label", connection.label)
					.withDetail("personUrn", connection.linkedInUserUrn ?: "")
					.withDetail("organizationUrn", connection.linkedInOrganizationUrn ?: "")
					.withDetail("lastError", connection.lastError ?: "")
					.build()
			}
			.defaultIfEmpty(
				Health.up()
					.withDetail("status", LinkedInConnectionStatus.NOT_CONNECTED.name)
					.build(),
			)
			.onErrorResume { ex ->
				// Erreur technique du check uniquement
				Mono.just(
					Health.up()
						.withDetail("status", "CHECK_FAILED")
						.withDetail("error", ex.message ?: "health check failed")
						.build(),
				)
			}
}

@Component("scheduler")
class SchedulerHealthIndicator(
	private val organizationSettingsService: OrganizationSettingsService,
	private val publicationSchedulerService: PublicationSchedulerService,
) : ReactiveHealthIndicator {

	override fun health(): Mono<Health> =
		organizationSettingsService.getSettings()
			.map { settings ->
				Health.up()
					.withDetail("enabled", settings.schedulerEnabled)
					.withDetail("publishHourUtc", settings.publishHourUtc)
					.withDetail("timezone", settings.timezone)
					.withDetail(
						"lastRunDate",
						publicationSchedulerService.lastScheduledRunDate()?.toString() ?: "never",
					)
					.withDetail("activeLinkedInAccountId", settings.activeLinkedInAccountId ?: "")
					.build()
			}
			.onErrorResume { ex ->
				Mono.just(
					Health.up()
						.withDetail("error", ex.message ?: "health check failed")
						.build(),
				)
			}
}
