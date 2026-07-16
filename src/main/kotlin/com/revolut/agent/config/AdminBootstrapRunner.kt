package com.revolut.agent.config

import com.revolut.agent.model.user.AppRole
import com.revolut.agent.model.user.AppUser
import com.revolut.agent.repository.user.AppUserRepository
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

	override fun run(args: ApplicationArguments) {
		appUserRepository.count()
			.flatMap { count ->
				if (count > 0) {
					return@flatMap Mono.empty<Void>()
				}

				val admin = AppUser(
					email = bootstrapProperties.adminEmail,
					passwordHash = requireNotNull(passwordEncoder.encode(bootstrapProperties.adminPassword)),
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
									"(email='{}'). Override ADMIN_EMAIL and ADMIN_PASSWORD before deploying to production.",
								bootstrapProperties.adminEmail,
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
		private const val DEFAULT_ADMIN_EMAIL = "bendjibril789@gmail.com"
		private const val DEFAULT_ADMIN_PASSWORD = "BenDjibril78#"
	}
}
