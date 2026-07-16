package com.revolut.agent.repository.settings
import com.revolut.agent.model.settings.OrganizationSettings
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface OrganizationSettingsRepository : ReactiveMongoRepository<OrganizationSettings, String> {

	fun findFirstByOrderByUpdatedAtDesc(): Mono<OrganizationSettings>
}
