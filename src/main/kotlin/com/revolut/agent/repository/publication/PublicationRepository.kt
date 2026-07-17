package com.revolut.agent.repository.publication

import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PublicationRepository : ReactiveMongoRepository<Publication, String> {

	fun findAllByOrderByTriggeredAtDesc(): Flux<Publication>

	fun existsByStatus(status: PublicationStatus): Mono<Boolean>
}
