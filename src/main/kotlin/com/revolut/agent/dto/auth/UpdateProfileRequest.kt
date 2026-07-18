package com.revolut.agent.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
	@field:NotBlank(message = "Username is required")
	@field:Size(min = 2, max = 64, message = "Username must be between 2 and 64 characters")
	@field:Pattern(
		regexp = "^[a-zA-Z0-9._-]+$",
		message = "Username may only contain letters, digits, dots, underscores and hyphens",
	)
	val username: String,

	@field:NotBlank(message = "Email is required")
	@field:Email(message = "Email must be valid")
	@field:Size(max = 255, message = "Email must be at most 255 characters")
	val email: String,
)
