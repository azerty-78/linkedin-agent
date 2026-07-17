package com.revolut.agent.dto.linkedin

import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import java.time.Instant

data class LinkedInStatusResponse(
	val status: LinkedInConnectionStatus,
	val linkedInUserUrn: String?,
	val linkedInOrganizationUrn: String?,
	val connectedAt: Instant?,
	val lastError: String?,
)
