package com.revolut.agent.dto.auth
data class LoginResponse(
	val accessToken: String,
	val refreshToken: String,
	val expiresIn: Long,
	val email: String,
)
