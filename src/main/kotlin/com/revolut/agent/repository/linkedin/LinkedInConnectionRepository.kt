package com.revolut.agent.repository.linkedin

import com.revolut.agent.model.linkedin.LinkedInConnection
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface LinkedInConnectionRepository : ReactiveMongoRepository<LinkedInConnection, String> {

	fun findAllByOrderByCreatedAtAsc(): Flux<LinkedInConnection>
}
