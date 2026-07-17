package com.revolut.agent.config.health

import com.revolut.agent.model.linkedin.LinkedInConnection
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.service.publication.PublicationSchedulerService
import com.revolut.agent.service.settings.OrganizationSettingsService
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component("linkedin")
class LinkedInHealthIndicator(
	private val linkedInConnectionRepository: LinkedInConnectionRepository,
) : ReactiveHealthIndicator {

	override fun health(): Mono<Health> =
		linkedInConnectionRepository.findById(LinkedInConnection.SINGLETON_ID)
			.map { connection ->
				if (connection.status == LinkedInConnectionStatus.CONNECTED) {
					Health.up()
						.withDetail("status", connection.status.name)
						.withDetail("organizationUrn", connection.linkedInOrganizationUrn ?: "")
						.build()
				} else {
					Health.down()
						.withDetail("status", connection.status.name)
						.withDetail("lastError", connection.lastError ?: "")
						.build()
				}
			}
			.defaultIfEmpty(
				Health.down()
					.withDetail("status", LinkedInConnectionStatus.NOT_CONNECTED.name)
					.build(),
			)
			.onErrorResume { ex ->
				Mono.just(
					Health.down(ex)
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
					.build()
			}
			.onErrorResume { ex ->
				Mono.just(
					Health.down(ex)
						.withDetail("error", ex.message ?: "health check failed")
						.build(),
				)
			}
}
