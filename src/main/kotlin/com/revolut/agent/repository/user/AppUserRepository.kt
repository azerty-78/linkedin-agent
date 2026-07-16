package com.revolut.agent.repository

import com.revolut.agent.model.AppUser
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface AppUserRepository : ReactiveMongoRepository<AppUser, String> {

	fun findByEmail(email: String): Mono<AppUser>
}
