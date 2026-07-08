package com.revolut.agent.exception

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
}
