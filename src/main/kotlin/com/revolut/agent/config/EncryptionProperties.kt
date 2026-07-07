package com.revolut.agent.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
@ConfigurationProperties(prefix = "app.encryption")
class EncryptionProperties(
	private val environment: Environment,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	var key: String = ""

	private var _keyBytes: ByteArray? = null

	val keyBytes: ByteArray
		get() = requireNotNull(_keyBytes) { "Encryption key has not been initialized" }

	@PostConstruct
	fun init() {
		var resolvedKey = key.trim()

		if (resolvedKey.isBlank()) {
			if (environment.activeProfiles.contains("prod")) {
				throw IllegalStateException(
					"app.encryption.key (ENCRYPTION_KEY) must be set in production",
				)
			}

			val generated = ByteArray(AES_KEY_SIZE_BYTES)
			SecureRandom().nextBytes(generated)
			resolvedKey = Base64.getEncoder().encodeToString(generated)
			log.warn(
				"ENCRYPTION_KEY is not set. Using an ephemeral dev encryption key. " +
					"Copy this value into your .env to persist encrypted settings across restarts: " +
					"ENCRYPTION_KEY={}",
				resolvedKey,
			)
		}

		val decoded = Base64.getDecoder().decode(resolvedKey)
		require(decoded.size == AES_KEY_SIZE_BYTES) {
			"app.encryption.key must decode to exactly $AES_KEY_SIZE_BYTES bytes for AES-256 " +
				"(got ${decoded.size} bytes after Base64 decoding)"
		}
		_keyBytes = decoded
	}

	companion object {
		private const val AES_KEY_SIZE_BYTES = 32
	}
}
