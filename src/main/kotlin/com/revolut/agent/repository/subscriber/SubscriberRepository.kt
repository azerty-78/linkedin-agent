package com.revolut.agent.repository

import com.revolut.agent.model.Subscriber
import com.revolut.agent.model.SubscriberStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface SubscriberRepository : ReactiveMongoRepository<Subscriber, String> {

	fun findByEmail(email: String): Mono<Subscriber>

	fun findByUnsubscribeToken(token: String): Mono<Subscriber>

	fun findAllByStatus(status: SubscriberStatus): Flux<Subscriber>

	fun countByStatus(status: SubscriberStatus): Mono<Long>
}
