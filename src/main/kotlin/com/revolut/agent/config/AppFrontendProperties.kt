package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class AppFrontendProperties {

	/** URL du frontend (redirect OAuth LinkedIn après callback). Sans slash final recommandé. */
	var frontendUrl: String = "http://localhost:3000"

	/**
	 * Origins CORS autorisées, séparées par des virgules.
	 * Si vide → dérivé de [frontendUrl] uniquement.
	 */
	var corsAllowedOrigins: String = ""

	fun resolvedCorsOrigins(): List<String> {
		val fromProperty = corsAllowedOrigins
			.split(',')
			.map { it.trim().trimEnd('/') }
			.filter { it.isNotEmpty() }
		if (fromProperty.isNotEmpty()) {
			return fromProperty.distinct()
		}
		val front = frontendUrl.trim().trimEnd('/')
		return if (front.isNotEmpty()) listOf(front) else emptyList()
	}
}
