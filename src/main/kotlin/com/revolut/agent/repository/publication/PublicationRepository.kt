package com.revolut.agent.repository.publication

import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationTriggerType
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface PublicationRepository : ReactiveMongoRepository<Publication, String> {

	fun findAllByOrderByTriggeredAtDesc(): Flux<Publication>

	fun existsByStatus(status: PublicationStatus): Mono<Boolean>

	fun existsByStatusIn(statuses: Collection<PublicationStatus>): Mono<Boolean>

	fun findByStatusAndScheduledPublishAtLessThanEqualOrderByScheduledPublishAtAsc(
		status: PublicationStatus,
		publishAt: Instant,
	): Flux<Publication>

	fun existsByTriggerTypeAndStatusInAndTriggeredAtGreaterThanEqualAndTriggeredAtLessThan(
		triggerType: PublicationTriggerType,
		statuses: Collection<PublicationStatus>,
		from: Instant,
		to: Instant,
	): Mono<Boolean>
}
