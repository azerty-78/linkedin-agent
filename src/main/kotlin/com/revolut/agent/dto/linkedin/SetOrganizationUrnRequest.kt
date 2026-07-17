package com.revolut.agent.dto.linkedin

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class SetOrganizationUrnRequest(
	@field:NotBlank
	@field:Pattern(
		regexp = "^urn:li:organization:.+",
		message = "organizationUrn must start with urn:li:organization:",
	)
	val organizationUrn: String,
)
