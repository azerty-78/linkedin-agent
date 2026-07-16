package com.revolut.agent.dto.subscriber
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CreateSubscriberRequest(
	@field:Email
	@field:NotBlank
	val email: String,

	val fullName: String? = null,
)
