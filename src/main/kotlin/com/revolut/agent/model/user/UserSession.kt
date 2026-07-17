package com.revolut.agent.model.user

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Session JWT active (1 document = 1 appareil / onglet connecté).
 * Max [MAX_CONCURRENT] par compte — au-delà, la plus ancienne est révoquée.
 */
@Document(collection = "user_sessions")
data class UserSession(
	@Id
	val id: String? = null,
	@Indexed(unique = true)
	val jti: String,
	@Indexed
	val userEmail: String,
	val userId: String?,
	val createdAt: Instant = Instant.now(),
	/** TTL Mongo : document purgé automatiquement après cette date. */
	@Indexed(expireAfter = "0s")
	val expiresAt: Instant,
) {
	companion object {
		const val MAX_CONCURRENT = 3
	}
}
