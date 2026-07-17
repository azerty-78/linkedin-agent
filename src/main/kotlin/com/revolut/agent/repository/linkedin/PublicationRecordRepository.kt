package com.revolut.agent.repository.linkedin

import com.revolut.agent.model.linkedin.PublicationRecord
import com.revolut.agent.model.linkedin.PublicationStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PublicationRecordRepository : ReactiveMongoRepository<PublicationRecord, String> {

	fun findAllByOrderByCreatedAtDesc(): Flux<PublicationRecord>

	fun findFirstByStatusOrderByCreatedAtDesc(status: PublicationStatus): Mono<PublicationRecord>
}
