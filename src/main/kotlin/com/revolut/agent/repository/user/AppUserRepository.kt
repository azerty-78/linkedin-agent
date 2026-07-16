package com.revolut.agent.repository.user
import com.revolut.agent.model.user.AppUser
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface AppUserRepository : ReactiveMongoRepository<AppUser, String> {

	fun findByEmail(email: String): Mono<AppUser>
}
