package com.revolut.agent.service.auth

import com.revolut.agent.config.JwtProperties
import com.revolut.agent.dto.auth.ChangePasswordRequest
import com.revolut.agent.dto.auth.LoginRequest
import com.revolut.agent.dto.auth.LoginResponse
import com.revolut.agent.dto.auth.MeResponse
import com.revolut.agent.dto.auth.UpdateProfileRequest
import com.revolut.agent.exception.auth.ProfileConflictException
import com.revolut.agent.model.user.AppUser
import com.revolut.agent.repository.user.AppUserRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
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
	private val userSessionService: UserSessionService,
) {

	fun login(request: LoginRequest): Mono<LoginResponse> {
		val email = request.email.trim().lowercase()
		return appUserRepository.findByEmail(email)
			.flatMap { user ->
				if (!passwordEncoder.matches(request.password, user.passwordHash)) {
					Mono.error(BadCredentialsException(INVALID_CREDENTIALS_MESSAGE))
				} else {
					val updatedUser = user.copy(lastLoginAt = Instant.now(), updatedAt = Instant.now())
					appUserRepository.save(updatedUser)
						.flatMap { savedUser ->
							userSessionService.openSession(savedUser)
								.map { jti ->
									LoginResponse(
										accessToken = jwtService.generateAccessToken(savedUser, jti),
										refreshToken = jwtService.generateRefreshToken(savedUser, jti),
										expiresIn = jwtProperties.expiration / 1000,
										email = savedUser.email,
									)
								}
						}
				}
			}
			.switchIfEmpty(Mono.error(BadCredentialsException(INVALID_CREDENTIALS_MESSAGE)))
	}

	fun logout(accessToken: String): Mono<Void> {
		val jti = jwtService.extractJti(accessToken) ?: return Mono.empty()
		return userSessionService.revokeByJti(jti)
	}

	fun getMe(authentication: Authentication): Mono<MeResponse> =
		resolveCurrentUser(authentication).map(::toMeResponse)

	fun updateProfile(authentication: Authentication, request: UpdateProfileRequest): Mono<MeResponse> {
		val newEmail = request.email.trim().lowercase()
		val newUsername = request.username.trim()

		return resolveCurrentUser(authentication)
			.flatMap { user ->
				val emailUnchanged = user.email.equals(newEmail, ignoreCase = true)
				val usernameUnchanged = user.username == newUsername

				val emailCheck = if (emailUnchanged) {
					Mono.just(false)
				} else {
					appUserRepository.existsByEmail(newEmail)
				}
				val usernameCheck = if (usernameUnchanged) {
					Mono.just(false)
				} else {
					appUserRepository.existsByUsername(newUsername)
				}

				Mono.zip(emailCheck, usernameCheck)
					.flatMap { tuple ->
						val emailTaken = tuple.t1
						val usernameTaken = tuple.t2
						when {
							emailTaken -> Mono.error(ProfileConflictException("Email already in use"))
							usernameTaken -> Mono.error(ProfileConflictException("Username already in use"))
							else -> {
								val oldEmail = user.email
								val updated = user.copy(
									email = newEmail,
									username = newUsername,
									updatedAt = Instant.now(),
								)
								appUserRepository.save(updated)
									.flatMap { saved ->
										if (oldEmail != saved.email) {
											userSessionService.renameUserEmail(oldEmail, saved.email)
												.thenReturn(saved)
										} else {
											Mono.just(saved)
										}
									}
									.map(::toMeResponse)
							}
						}
					}
			}
	}

	fun changePassword(
		authentication: Authentication,
		request: ChangePasswordRequest,
		accessToken: String?,
	): Mono<Void> =
		resolveCurrentUser(authentication)
			.flatMap { user ->
				if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
					return@flatMap Mono.error<Void>(
						IllegalArgumentException("Current password is incorrect"),
					)
				}
				val newPassword = request.newPassword
				if (newPassword.length < 8) {
					return@flatMap Mono.error<Void>(
						IllegalArgumentException("New password must be at least 8 characters"),
					)
				}
				val updated = user.copy(
					passwordHash = requireNotNull(passwordEncoder.encode(newPassword)),
					updatedAt = Instant.now(),
				)
				appUserRepository.save(updated)
					.flatMap {
						val keepJti = accessToken?.let { jwtService.extractJti(it) }
						userSessionService.revokeOtherSessions(user.email, keepJti)
					}
			}

	private fun resolveCurrentUser(authentication: Authentication): Mono<AppUser> {
		val userId = (authentication.credentials as? String)?.takeIf { it.isNotBlank() }
		val byId = if (userId != null) {
			appUserRepository.findById(userId)
		} else {
			Mono.empty()
		}
		return byId.switchIfEmpty(
			appUserRepository.findByEmail(authentication.name.trim().lowercase()),
		).switchIfEmpty(
			Mono.error(BadCredentialsException("User not found")),
		)
	}

	private fun toMeResponse(user: AppUser): MeResponse =
		MeResponse(
			id = requireNotNull(user.id) { "User id must not be null" },
			email = user.email,
			username = user.username,
			role = user.role.name,
			createdAt = user.createdAt,
			updatedAt = user.updatedAt,
		)

	companion object {
		private const val INVALID_CREDENTIALS_MESSAGE = "Invalid credentials"
	}
}
