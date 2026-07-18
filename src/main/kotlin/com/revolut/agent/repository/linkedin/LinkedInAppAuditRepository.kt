package com.revolut.agent.repository.linkedin

import com.revolut.agent.model.linkedin.LinkedInAppAuditEvent
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface LinkedInAppAuditRepository : ReactiveMongoRepository<LinkedInAppAuditEvent, String> {

	fun findAllByOrganizationIdOrderByCreatedAtDesc(organizationId: String): Flux<LinkedInAppAuditEvent>

	fun countByOrganizationId(organizationId: String): Mono<Long>
}
