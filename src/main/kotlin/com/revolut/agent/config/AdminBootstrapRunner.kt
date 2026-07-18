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

/**
 * Seeds the ADMIN user **only if missing**.
 * Never overwrites password / email / username of an existing admin.
 */
@Component
class AdminBootstrapRunner(
	private val appUserRepository: AppUserRepository,
	private val passwordEncoder: PasswordEncoder,
	private val bootstrapProperties: BootstrapProperties,
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun run(args: ApplicationArguments) {
		val seedEmail = bootstrapProperties.adminEmail.trim().lowercase()
		val seedUsername = resolveSeedUsername(seedEmail)

		appUserRepository.findFirstByRole(AppRole.ADMIN)
			.switchIfEmpty(appUserRepository.findByEmail(seedEmail))
			.flatMap { existing ->
				backfillUsernameIfNeeded(existing, seedUsername)
					.doOnSuccess {
						log.info("Admin user already present — skip seed (email='{}')", existing.email)
					}
					.then(Mono.empty<Void>())
			}
			.switchIfEmpty(
				Mono.defer {
					val now = Instant.now()
					val admin = AppUser(
						email = seedEmail,
						username = seedUsername,
						passwordHash = requireNotNull(passwordEncoder.encode(bootstrapProperties.adminPassword)),
						role = AppRole.ADMIN,
						createdAt = now,
						updatedAt = now,
					)
					appUserRepository.save(admin)
						.doOnSuccess {
							log.warn(
								"Default admin account created (email='{}', username='{}'). " +
									"Change ADMIN_EMAIL / ADMIN_PASSWORD / ADMIN_USERNAME if using defaults.",
								seedEmail,
								seedUsername,
							)
							if (isUsingDefaultCredentials()) {
								log.warn(
									"SECURITY: default bootstrap credentials detected " +
										"(email='{}'). Override ADMIN_EMAIL and ADMIN_PASSWORD before production.",
									seedEmail,
								)
							}
						}
						.then()
				},
			)
			.block()
	}

	/**
	 * Existing documents created before `username` existed may miss the field after deserialization
	 * (empty string). Backfill once without touching the password.
	 */
	private fun backfillUsernameIfNeeded(user: AppUser, fallbackUsername: String): Mono<AppUser> {
		if (user.username.isNotBlank()) {
			return Mono.just(user)
		}
		val candidate = fallbackUsername.ifBlank { usernameFromEmail(user.email) }
		return appUserRepository.findByUsername(candidate)
			.flatMap { clash ->
				if (clash.id == user.id) {
					Mono.just(candidate)
				} else {
					Mono.just("${candidate}_${user.id?.takeLast(6) ?: "admin"}")
				}
			}
			.defaultIfEmpty(candidate)
			.flatMap { username ->
				appUserRepository.save(
					user.copy(
						username = username,
						updatedAt = Instant.now(),
					),
				).doOnSuccess {
					log.info("Backfilled username='{}' for admin id={}", username, user.id)
				}
			}
	}

	private fun resolveSeedUsername(email: String): String {
		val configured = bootstrapProperties.adminUsername.trim()
		if (configured.isNotEmpty()) {
			return configured
		}
		return usernameFromEmail(email)
	}

	private fun usernameFromEmail(email: String): String {
		val local = email.substringBefore("@").ifBlank { "admin" }
		val sanitized = local.replace(Regex("[^a-zA-Z0-9._-]"), "_")
		return sanitized.take(64).ifBlank { "admin" }
	}

	private fun isUsingDefaultCredentials(): Boolean =
		bootstrapProperties.adminEmail.trim().equals(DEFAULT_ADMIN_EMAIL, ignoreCase = true) &&
			bootstrapProperties.adminPassword == DEFAULT_ADMIN_PASSWORD

	companion object {
		private const val DEFAULT_ADMIN_EMAIL = "bendjibril789@gmail.com"
		private const val DEFAULT_ADMIN_PASSWORD = "BenDjibril78#"
	}
}
