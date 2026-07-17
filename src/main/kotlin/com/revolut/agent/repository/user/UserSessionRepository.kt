package com.revolut.agent.repository.user

import com.revolut.agent.model.user.UserSession
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UserSessionRepository : ReactiveMongoRepository<UserSession, String> {

	fun findByJti(jti: String): Mono<UserSession>

	fun findAllByUserEmailOrderByCreatedAtAsc(userEmail: String): Flux<UserSession>

	fun deleteByJti(jti: String): Mono<Void>

	fun deleteAllByUserEmail(userEmail: String): Mono<Void>
}
