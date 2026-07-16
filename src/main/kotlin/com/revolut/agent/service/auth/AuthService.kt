package com.revolut.agent.service.auth
import com.revolut.agent.config.JwtProperties
import com.revolut.agent.dto.auth.LoginRequest
import com.revolut.agent.dto.auth.LoginResponse
import com.revolut.agent.repository.user.AppUserRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class AuthService(
	private val appUserRepository: AppUserRepository,
	private val jwtService: JwtService,
	private val jwtProperties: JwtProperties,
	private val passwordEncoder: PasswordEncoder,
) {

	fun login(request: LoginRequest): Mono<LoginResponse> =
		appUserRepository.findByEmail(request.email.trim())
			.flatMap { user ->
				if (!passwordEncoder.matches(request.password, user.passwordHash)) {
					Mono.error(BadCredentialsException(INVALID_CREDENTIALS_MESSAGE))
				} else {
					val updatedUser = user.copy(lastLoginAt = Instant.now())
					appUserRepository.save(updatedUser)
						.map { savedUser ->
							LoginResponse(
								accessToken = jwtService.generateAccessToken(savedUser),
								refreshToken = jwtService.generateRefreshToken(savedUser),
								expiresIn = jwtProperties.expiration / 1000,
								email = savedUser.email,
							)
						}
				}
			}
			.switchIfEmpty(Mono.error(BadCredentialsException(INVALID_CREDENTIALS_MESSAGE)))

	companion object {
		private const val INVALID_CREDENTIALS_MESSAGE = "Invalid credentials"
	}
}
