package com.revolut.agent.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CreateSubscriberRequest(
	@field:Email
	@field:NotBlank
	val email: String,

	val fullName: String? = null,
)
