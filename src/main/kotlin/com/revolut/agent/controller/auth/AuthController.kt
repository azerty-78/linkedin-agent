package com.revolut.agent.controller.auth
import com.revolut.agent.dto.auth.LoginRequest
import com.revolut.agent.dto.auth.LoginResponse
import com.revolut.agent.dto.auth.MeResponse
import com.revolut.agent.service.auth.AuthService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
		authentication.map { auth ->
			MeResponse(
				email = auth.name,
				role = auth.authorities
					.firstOrNull()
					?.authority
					?.removePrefix("ROLE_")
					?: "ADMIN",
			)
		}
}
