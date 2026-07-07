package com.revolut.agent.config

import com.revolut.agent.model.AppRole
import com.revolut.agent.model.AppUser
import com.revolut.agent.repository.AppUserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant

@Component
class AdminBootstrapRunner(
	private val appUserRepository: AppUserRepository,
	private val passwordEncoder: PasswordEncoder,
	private val bootstrapProperties: BootstrapProperties,
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun run(args: ApplicationArguments?) {
		appUserRepository.count()
			.flatMap { count ->
				if (count > 0) {
					return@flatMap Mono.empty()
				}

				val admin = AppUser(
					email = bootstrapProperties.adminEmail,
					passwordHash = passwordEncoder.encode(bootstrapProperties.adminPassword),
					role = AppRole.ADMIN,
					createdAt = Instant.now(),
				)

				appUserRepository.save(admin)
					.doOnSuccess {
						log.warn(
							"Default admin account created (email='{}'). " +
								"Change ADMIN_EMAIL and ADMIN_PASSWORD immediately if using default bootstrap credentials.",
							bootstrapProperties.adminEmail,
						)
						if (isUsingDefaultCredentials()) {
							log.warn(
								"SECURITY: default bootstrap credentials detected " +
									"(admin@localhost / ChangeMe123!). Change them before deploying to production.",
							)
						}
					}
			}
			.block()
	}

	private fun isUsingDefaultCredentials(): Boolean =
		bootstrapProperties.adminEmail == DEFAULT_ADMIN_EMAIL &&
			bootstrapProperties.adminPassword == DEFAULT_ADMIN_PASSWORD

	companion object {
		private const val DEFAULT_ADMIN_EMAIL = "admin@localhost"
		private const val DEFAULT_ADMIN_PASSWORD = "ChangeMe123!"
	}
}
