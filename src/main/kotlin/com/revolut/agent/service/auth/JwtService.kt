package com.revolut.agent.service.auth

import com.revolut.agent.config.JwtProperties
import com.revolut.agent.model.user.AppUser
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
	private val jwtProperties: JwtProperties,
) {

	private val signingKey: SecretKey by lazy {
		Keys.hmacShaKeyFor(
			Base64.getEncoder().encode(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)),
		)
	}

	fun generateAccessToken(user: AppUser, jti: String = UUID.randomUUID().toString()): String =
		buildToken(user, TOKEN_TYPE_ACCESS, jwtProperties.expiration, jti)

	fun generateRefreshToken(user: AppUser, jti: String = UUID.randomUUID().toString()): String =
		buildToken(user, TOKEN_TYPE_REFRESH, REFRESH_TOKEN_EXPIRATION_MS, jti)

	fun validateToken(token: String): Boolean =
		try {
			val claims = extractAllClaims(token)
			claims[CLAIM_TYPE] == TOKEN_TYPE_ACCESS && !claims.expiration.before(Date())
		} catch (_: Exception) {
			false
		}

	fun extractEmail(token: String): String =
		extractAllClaims(token).subject

	fun extractUserId(token: String): String? =
		try {
			extractAllClaims(token)[CLAIM_UID]?.toString()?.takeIf { it.isNotBlank() }
		} catch (_: Exception) {
			null
		}

	fun extractJti(token: String): String? =
		try {
			extractAllClaims(token).id?.takeIf { it.isNotBlank() }
		} catch (_: Exception) {
			null
		}

	fun extractExpiration(token: String): Date =
		extractAllClaims(token).expiration

	private fun buildToken(user: AppUser, type: String, expirationMs: Long, jti: String): String {
		val now = Date()
		val expiry = Date(now.time + expirationMs)

		val builder = Jwts.builder()
			.id(jti)
			.subject(user.email)
			.claim(CLAIM_ROLE, user.role.name)
			.claim(CLAIM_TYPE, type)
			.issuedAt(now)
			.expiration(expiry)
		user.id?.takeIf { it.isNotBlank() }?.let { builder.claim(CLAIM_UID, it) }
		return builder
			.signWith(signingKey, Jwts.SIG.HS256)
			.compact()
	}

	private fun extractAllClaims(token: String): Claims =
		Jwts.parser()
			.verifyWith(signingKey)
			.build()
			.parseSignedClaims(token)
			.payload

	companion object {
		private const val CLAIM_ROLE = "role"
		private const val CLAIM_TYPE = "type"
		private const val CLAIM_UID = "uid"
		private const val TOKEN_TYPE_ACCESS = "access"
		private const val TOKEN_TYPE_REFRESH = "refresh"
		private const val REFRESH_TOKEN_EXPIRATION_MS = 604_800_000L // 7 days
	}
}
