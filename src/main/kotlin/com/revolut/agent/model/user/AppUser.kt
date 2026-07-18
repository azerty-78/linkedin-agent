package com.revolut.agent.model.user

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "app_users")
data class AppUser(
	@Id
	val id: String? = null,
	@Indexed(unique = true)
	val email: String,
	@Indexed(unique = true)
	val username: String = "",
	val passwordHash: String,
	val role: AppRole = AppRole.ADMIN,
	val createdAt: Instant = Instant.now(),
	val updatedAt: Instant = Instant.now(),
	val lastLoginAt: Instant? = null,
)
