package com.revolut.agent.config

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
class JwtProperties {

	var secret: String = ""
	var expiration: Long = 86400000

	@PostConstruct
	fun validate() {
		require(secret.length >= 32) {
			"jwt.secret must be at least 32 characters long (current length: ${secret.length})"
		}
	}
}
