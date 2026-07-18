package com.revolut.agent.config

import com.revolut.agent.model.linkedin.LinkedInApp
import com.revolut.agent.model.linkedin.LinkedInAppAuditAction
import com.revolut.agent.model.linkedin.LinkedInAppAuditEvent
import com.revolut.agent.repository.linkedin.LinkedInAppAuditRepository
import com.revolut.agent.repository.linkedin.LinkedInAppRepository
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Migre une fois les credentials LinkedIn legacy (`OrganizationSettings`)
 * vers une [LinkedInApp] « Default » active.
 */
@Component
@Order(200)
class LinkedInAppMigrationRunner(
	private val organizationSettingsRepository: OrganizationSettingsRepository,
	private val linkedInAppRepository: LinkedInAppRepository,
	private val linkedInAppAuditRepository: LinkedInAppAuditRepository,
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun run(args: ApplicationArguments) {
		val orgId = LinkedInApp.DEFAULT_ORGANIZATION_ID
		linkedInAppRepository.countByOrganizationId(orgId)
			.flatMap { count ->
				if (count > 0L) {
					log.debug("LinkedIn apps already present ({}) — skip legacy migration", count)
					Mono.empty()
				} else {
					organizationSettingsRepository.findFirstByOrderByUpdatedAtDesc()
						.flatMap { settings ->
							val clientId = settings.linkedInClientId?.trim().orEmpty()
							val secret = settings.encryptedLinkedInClientSecret
							if (clientId.isBlank() || secret.isNullOrBlank()) {
								log.info("No legacy LinkedIn app credentials to migrate")
								Mono.empty()
							} else {
								val now = Instant.now()
								val app = LinkedInApp(
									organizationId = orgId,
									label = "Default",
									clientId = clientId,
									encryptedClientSecret = secret,
									isActive = true,
									createdAt = now,
									updatedAt = now,
								)
								linkedInAppRepository.save(app)
									.flatMap { saved ->
										linkedInAppAuditRepository.save(
											LinkedInAppAuditEvent(
												organizationId = orgId,
												appId = saved.id,
												appLabel = saved.label,
												action = LinkedInAppAuditAction.CREATED,
												summary = "Migration legacy settings → application « Default »",
												actorEmail = "system",
												createdAt = now,
											),
										).thenReturn(saved)
									}
									.doOnNext {
										log.info(
											"Migrated legacy LinkedIn credentials to app id={} label=Default",
											it.id,
										)
									}
									.then()
							}
						}
						.switchIfEmpty(Mono.empty())
				}
			}
			.block()
	}
}
