package com.revolut.agent.repository.linkedin

import com.revolut.agent.model.linkedin.LinkedInApp
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface LinkedInAppRepository : ReactiveMongoRepository<LinkedInApp, String> {

	fun findAllByOrganizationIdOrderByCreatedAtAsc(organizationId: String): Flux<LinkedInApp>

	fun countByOrganizationId(organizationId: String): Mono<Long>

	fun findByOrganizationIdAndIsActiveTrue(organizationId: String): Mono<LinkedInApp>

	fun findByIdAndOrganizationId(id: String, organizationId: String): Mono<LinkedInApp>

	fun existsByOrganizationIdAndClientId(organizationId: String, clientId: String): Mono<Boolean>

	fun existsByOrganizationIdAndClientIdAndIdNot(
		organizationId: String,
		clientId: String,
		id: String,
	): Mono<Boolean>
}
