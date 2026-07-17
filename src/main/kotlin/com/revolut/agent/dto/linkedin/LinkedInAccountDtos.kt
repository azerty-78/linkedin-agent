package com.revolut.agent.dto.linkedin

import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import java.time.Instant

data class LinkedInStatusResponse(
	val id: String?,
	val label: String,
	val status: LinkedInConnectionStatus,
	val linkedInUserUrn: String?,
	val linkedInOrganizationUrn: String?,
	val connectedAt: Instant?,
	val lastError: String?,
	val isActive: Boolean = false,
)

data class LinkedInAccountListResponse(
	val accounts: List<LinkedInStatusResponse>,
	val maxAccounts: Int,
	val activeLinkedInAccountId: String?,
)

data class UpdateLinkedInAccountRequest(
	val label: String? = null,
)

data class LinkedInAuthUrlResponse(
	val authorizationUrl: String,
	/** Id du compte en cours de (re)connexion ; null si nouveau compte. */
	val accountId: String? = null,
)
