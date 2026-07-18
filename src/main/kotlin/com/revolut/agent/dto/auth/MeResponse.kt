package com.revolut.agent.dto.auth

import java.time.Instant

data class MeResponse(
	val id: String,
	val email: String,
	val username: String,
	val role: String,
	val createdAt: Instant,
	val updatedAt: Instant,
)
