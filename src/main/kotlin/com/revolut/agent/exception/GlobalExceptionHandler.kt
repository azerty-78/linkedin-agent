package com.revolut.agent.exception

import com.revolut.agent.exception.ai.AiGenerationException
import com.revolut.agent.exception.auth.ProfileConflictException
import com.revolut.agent.exception.linkedin.LinkedInAccountLimitException
import com.revolut.agent.exception.linkedin.LinkedInPublishingException
import com.revolut.agent.exception.publication.PublicationAlreadyRunningException
import com.revolut.agent.service.linkedin.LinkedInOAuthException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import reactor.core.publisher.Mono

@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(ProfileConflictException::class)
	fun handleProfileConflict(ex: ProfileConflictException): Mono<ResponseEntity<Map<String, Any>>> =
		Mono.just(
			ResponseEntity.status(HttpStatus.CONFLICT)
				.body(
					mapOf(
						"error" to (ex.message ?: "Conflict"),
						"status" to 409,
					),
				),
		)

	@ExceptionHandler(LinkedInAccountLimitException::class)
	fun handleLinkedInAccountLimit(ex: LinkedInAccountLimitException): Mono<ResponseEntity<Map<String, Any>>> =
		Mono.just(
			ResponseEntity.status(HttpStatus.CONFLICT)
				.body(
					mapOf(
						"error" to (ex.message ?: "LinkedIn account limit reached"),
						"status" to 409,
					),
				),
		)

	@ExceptionHandler(PublicationAlreadyRunningException::class)
	fun handlePublicationAlreadyRunning(
		ex: PublicationAlreadyRunningException,
	): Mono<ResponseEntity<Map<String, Any>>> =
		Mono.just(
			ResponseEntity.status(HttpStatus.CONFLICT)
				.body(
					mapOf(
						"error" to (ex.message ?: "Publication already running"),
						"status" to 409,
					),
				),
		)

	@ExceptionHandler(LinkedInPublishingException::class)
	fun handleLinkedInPublishing(ex: LinkedInPublishingException): Mono<ResponseEntity<Map<String, Any>>> =
		Mono.just(
			ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(
					mapOf(
						"error" to (ex.message ?: "LinkedIn publishing precondition failed"),
						"status" to 422,
					),
				),
		)

	@ExceptionHandler(WebExchangeBindException::class)
	fun handleValidationException(ex: WebExchangeBindException): Mono<ResponseEntity<Map<String, Any>>> {
		val message = ex.bindingResult.fieldErrors
			.firstOrNull()
			?.defaultMessage
			?: "Validation failed"
		return Mono.just(
			ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(
					mapOf(
						"error" to message,
						"status" to 400,
					),
				),
		)
	}

	@ExceptionHandler(IllegalStateException::class)
	fun handleIllegalState(ex: IllegalStateException): Mono<ResponseEntity<Map<String, Any>>> {
		val status = if (ex.message?.contains("LinkedIn", ignoreCase = true) == true) {
			HttpStatus.UNPROCESSABLE_ENTITY
		} else {
			HttpStatus.BAD_REQUEST
		}
		return Mono.just(
			ResponseEntity.status(status)
				.body(
					mapOf(
						"error" to (ex.message ?: "Illegal state"),
						"status" to status.value(),
					),
				),
		)
	}

	@ExceptionHandler(IllegalArgumentException::class)
	fun handleIllegalArgument(ex: IllegalArgumentException): Mono<ResponseEntity<Map<String, Any>>> =
		Mono.just(
			ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(
					mapOf(
						"error" to (ex.message ?: "Bad request"),
						"status" to 400,
					),
				),
		)

	@ExceptionHandler(LinkedInOAuthException::class)
	fun handleLinkedInOAuth(ex: LinkedInOAuthException): Mono<ResponseEntity<Map<String, Any>>> =
		Mono.just(
			ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(
					mapOf(
						"error" to (ex.message ?: "LinkedIn OAuth failed"),
						"status" to 400,
					),
				),
		)

	@ExceptionHandler(BadCredentialsException::class, AuthenticationException::class)
	fun handleAuthenticationException(ex: AuthenticationException): Mono<ResponseEntity<Map<String, Any>>> =
		Mono.just(
			ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(
					mapOf(
						"error" to (ex.message ?: "Invalid credentials"),
						"status" to 401,
					),
				),
		)

	@ExceptionHandler(AiGenerationException::class)
	fun handleAiGenerationException(
		ex: AiGenerationException,
	): Mono<ResponseEntity<Map<String, Any>>> {
		val status = if (
			ex.message?.contains("No API key configured", ignoreCase = true) == true ||
			ex.message?.contains("Clé API", ignoreCase = true) == true ||
			ex.message?.contains("manquante", ignoreCase = true) == true
		) {
			HttpStatus.UNPROCESSABLE_ENTITY
		} else {
			HttpStatus.BAD_REQUEST
		}
		return Mono.just(
			ResponseEntity.status(status)
				.body(
					mapOf(
						"error" to (ex.message ?: "AI generation failed"),
						"status" to status.value(),
					),
				),
		)
	}
}
