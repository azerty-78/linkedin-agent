package com.revolut.agent.service.auth

import com.revolut.agent.config.JwtProperties
import com.revolut.agent.model.user.AppUser
import com.revolut.agent.model.user.UserSession
import com.revolut.agent.repository.user.UserSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

@Service
class UserSessionService(
	private val userSessionRepository: UserSessionRepository,
	private val jwtProperties: JwtProperties,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	/**
	 * Crée une session pour le login. Si déjà [UserSession.MAX_CONCURRENT] sessions,
	 * révoque la/les plus ancienne(s) pour rester à 3.
	 */
	fun openSession(user: AppUser): Mono<String> {
		val email = user.email
		val jti = UUID.randomUUID().toString()
		val now = Instant.now()
		val expiresAt = now.plusMillis(jwtProperties.expiration)

		return userSessionRepository.findAllByUserEmailOrderByCreatedAtAsc(email)
			.collectList()
			.flatMap { existing ->
				val active = existing.filter { it.expiresAt.isAfter(now) }
				val toRevoke = if (active.size >= UserSession.MAX_CONCURRENT) {
					active.take(active.size - UserSession.MAX_CONCURRENT + 1)
				} else {
					emptyList()
				}
				val stale = existing.filter { !it.expiresAt.isAfter(now) }
				val deleteIds = (toRevoke + stale).mapNotNull { it.id }

				val deleteMono = if (deleteIds.isEmpty()) {
					Mono.empty()
				} else {
					userSessionRepository.deleteAllById(deleteIds)
						.doOnSuccess {
							if (toRevoke.isNotEmpty()) {
								log.info(
									"Revoked {} oldest session(s) for {} (max concurrent={})",
									toRevoke.size,
									email,
									UserSession.MAX_CONCURRENT,
								)
							}
						}
				}

				deleteMono.then(
					userSessionRepository.save(
						UserSession(
							jti = jti,
							userEmail = email,
							userId = user.id,
							createdAt = now,
							expiresAt = expiresAt,
						),
					),
				)
			}
			.map { jti }
	}

	fun isSessionActive(jti: String): Mono<Boolean> =
		userSessionRepository.findByJti(jti)
			.map { it.expiresAt.isAfter(Instant.now()) }
			.defaultIfEmpty(false)

	fun revokeByJti(jti: String): Mono<Void> =
		userSessionRepository.deleteByJti(jti)

	/** Updates session email when the user changes their login email. */
	fun renameUserEmail(oldEmail: String, newEmail: String): Mono<Void> {
		if (oldEmail == newEmail) {
			return Mono.empty()
		}
		return userSessionRepository.findAllByUserEmailOrderByCreatedAtAsc(oldEmail)
			.flatMap { session ->
				userSessionRepository.save(session.copy(userEmail = newEmail))
			}
			.then()
	}

	/** Revokes all sessions for [userEmail] except optionally [keepJti]. */
	fun revokeOtherSessions(userEmail: String, keepJti: String?): Mono<Void> =
		userSessionRepository.findAllByUserEmailOrderByCreatedAtAsc(userEmail)
			.filter { session -> keepJti == null || session.jti != keepJti }
			.flatMap { session ->
				session.id?.let { userSessionRepository.deleteById(it) } ?: Mono.empty()
			}
			.then()
}
