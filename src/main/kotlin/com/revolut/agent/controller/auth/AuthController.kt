package com.revolut.agent.controller.auth

import com.revolut.agent.dto.auth.ChangePasswordRequest
import com.revolut.agent.dto.auth.LoginRequest
import com.revolut.agent.dto.auth.LoginResponse
import com.revolut.agent.dto.auth.MeResponse
import com.revolut.agent.dto.auth.UpdateProfileRequest
import com.revolut.agent.service.auth.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/auth")
class AuthController(
	private val authService: AuthService,
) {

	@PostMapping("/login")
	fun login(@Valid @RequestBody request: LoginRequest): Mono<LoginResponse> =
		authService.login(request)

	@GetMapping("/me")
	fun me(authentication: Mono<Authentication>): Mono<MeResponse> =
		authentication.flatMap(authService::getMe)

	@PutMapping("/me")
	fun updateMe(
		authentication: Mono<Authentication>,
		@Valid @RequestBody request: UpdateProfileRequest,
	): Mono<MeResponse> =
		authentication.flatMap { auth -> authService.updateProfile(auth, request) }

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun changePassword(
		authentication: Mono<Authentication>,
		request: ServerHttpRequest,
		@Valid @RequestBody body: ChangePasswordRequest,
	): Mono<Void> {
		val accessToken = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
			?.takeIf { it.startsWith("Bearer ") }
			?.removePrefix("Bearer ")
			?.trim()
		return authentication.flatMap { auth ->
			authService.changePassword(auth, body, accessToken)
		}
	}

	/** Révoque la session courante (JWT). */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun logout(request: ServerHttpRequest): Mono<Void> {
		val header = request.headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return Mono.empty()
		if (!header.startsWith("Bearer ")) {
			return Mono.empty()
		}
		return authService.logout(header.removePrefix("Bearer ").trim())
	}
}
