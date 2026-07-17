package com.revolut.agent.controller.linkedin

import com.revolut.agent.config.AppFrontendProperties
import com.revolut.agent.service.linkedin.LinkedInOAuthService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Public OAuth callback — LinkedIn redirects the browser here without a JWT.
 */
@RestController
@RequestMapping("/api/linkedin")
class LinkedInCallbackController(
	private val linkedInOAuthService: LinkedInOAuthService,
	private val appFrontendProperties: AppFrontendProperties,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@GetMapping("/callback")
	fun callback(
		@RequestParam(required = false) code: String?,
		@RequestParam(required = false) state: String?,
		@RequestParam(required = false) error: String?,
		@RequestParam(name = "error_description", required = false) errorDescription: String?,
	): Mono<ResponseEntity<Void>> {
		if (!error.isNullOrBlank()) {
			val errorCode = error.trim()
			log.info("LinkedIn OAuth denied by user or provider | error={}", errorCode)
			return linkedInOAuthService.recordOAuthDenial(errorCode)
				.thenReturn(redirect(connected = false, error = errorCode))
		}

		if (code.isNullOrBlank() || state.isNullOrBlank()) {
			log.warn("LinkedIn callback missing code or state")
			return Mono.just(redirect(connected = false, error = "missing_code_or_state"))
		}

		return linkedInOAuthService.exchangeCodeForToken(code.trim(), state.trim())
			.thenReturn(redirect(connected = true, error = null))
			.onErrorResume { ex ->
				val message = ex.message?.take(120)?.ifBlank { null } ?: "oauth_failed"
				log.warn("LinkedIn OAuth callback failed: {}", message)
				Mono.just(redirect(connected = false, error = sanitizeQueryError(message)))
			}
	}

	private fun redirect(connected: Boolean, error: String?): ResponseEntity<Void> {
		val base = appFrontendProperties.frontendUrl.trimEnd('/')
		val target = if (connected) {
			"$base/dashboard/linkedin?connected=true"
		} else {
			val encoded = URLEncoder.encode(error ?: "unknown", StandardCharsets.UTF_8)
			"$base/dashboard/linkedin?connected=false&error=$encoded"
		}
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(target))
			.build()
	}

	private fun sanitizeQueryError(raw: String): String =
		raw.replace(Regex("[^a-zA-Z0-9._\\- ]"), "").take(80).ifBlank { "oauth_failed" }
}
