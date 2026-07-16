package com.revolut.agent.exception

import com.revolut.agent.exception.ai.AiGenerationException
import com.revolut.agent.exception.subscriber.SubscriberAlreadyActiveException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import reactor.core.publisher.Mono

@RestControllerAdvice
class GlobalExceptionHandler {

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

	@ExceptionHandler(SubscriberAlreadyActiveException::class)
	fun handleSubscriberAlreadyActive(
		ex: SubscriberAlreadyActiveException,
	): Mono<ResponseEntity<Map<String, Any>>> =
		Mono.just(
			ResponseEntity.status(HttpStatus.CONFLICT)
				.body(
					mapOf(
						"error" to (ex.message ?: "A subscriber with this email is already active"),
						"status" to 409,
					),
				),
		)

	@ExceptionHandler(AiGenerationException::class)
	fun handleAiGenerationException(
		ex: AiGenerationException,
	): Mono<ResponseEntity<Map<String, Any>>> {
		val status = if (ex.message?.contains("No API key configured", ignoreCase = true) == true) {
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
