package com.revolut.agent.repository.user

import com.revolut.agent.model.user.AppRole
import com.revolut.agent.model.user.AppUser
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface AppUserRepository : ReactiveMongoRepository<AppUser, String> {

	fun findByEmail(email: String): Mono<AppUser>

	fun findByUsername(username: String): Mono<AppUser>

	fun findFirstByRole(role: AppRole): Mono<AppUser>

	fun existsByEmail(email: String): Mono<Boolean>

	fun existsByUsername(username: String): Mono<Boolean>
}
