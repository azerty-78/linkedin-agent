package com.revolut.agent.repository.subscriber
import com.revolut.agent.model.subscriber.Subscriber
import com.revolut.agent.model.subscriber.SubscriberStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface SubscriberRepository : ReactiveMongoRepository<Subscriber, String> {

	fun findByEmail(email: String): Mono<Subscriber>

	fun findByUnsubscribeToken(token: String): Mono<Subscriber>

	fun findAllByStatus(status: SubscriberStatus): Flux<Subscriber>

	fun countByStatus(status: SubscriberStatus): Mono<Long>
}
