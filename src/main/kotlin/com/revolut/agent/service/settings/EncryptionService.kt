package com.revolut.agent.service.settings

import com.revolut.agent.config.EncryptionProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService(
	encryptionProperties: EncryptionProperties,
) {

	private val log = LoggerFactory.getLogger(javaClass)
	private val secretKey = SecretKeySpec(encryptionProperties.keyBytes, "AES")

	fun encrypt(plainText: String): String {
		val iv = ByteArray(GCM_IV_LENGTH_BYTES)
		SecureRandom().nextBytes(iv)

		val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
		val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

		return "${Base64.getEncoder().encodeToString(iv)}:" +
			Base64.getEncoder().encodeToString(ciphertext)
	}

	fun decrypt(cipherText: String): String {
		val parts = cipherText.split(":", limit = 2)
		require(parts.size == 2) { "Invalid encrypted value format, expected IV:ciphertext" }

		val iv = Base64.getDecoder().decode(parts[0])
		val encryptedBytes = Base64.getDecoder().decode(parts[1])

		val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
		val plainBytes = cipher.doFinal(encryptedBytes)

		return String(plainBytes, StandardCharsets.UTF_8)
	}

	/** Returns null if the payload cannot be decrypted (wrong ENCRYPTION_KEY, corrupt data). */
	fun tryDecrypt(cipherText: String): String? =
		try {
			decrypt(cipherText)
		} catch (ex: Exception) {
			log.warn(
				"Failed to decrypt value (ENCRYPTION_KEY mismatch or corrupt ciphertext): {}",
				ex.javaClass.simpleName,
			)
			null
		}

	fun mask(plainText: String?): String {
		if (plainText.isNullOrBlank()) {
			return ""
		}
		return if (plainText.length < 4) {
			"****"
		} else {
			"****${plainText.takeLast(4)}"
		}
	}

	companion object {
		private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
		private const val GCM_IV_LENGTH_BYTES = 12
		private const val GCM_TAG_LENGTH_BITS = 128
	}
}
