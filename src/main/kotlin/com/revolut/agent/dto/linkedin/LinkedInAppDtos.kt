package com.revolut.agent.dto.linkedin

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class LinkedInAppResponse(
	val id: String,
	val label: String,
	val clientId: String,
	val clientSecretMasked: String,
	val isActive: Boolean,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class LinkedInAppListResponse(
	val apps: List<LinkedInAppResponse>,
	val maxApps: Int,
	val activeAppId: String?,
)

data class CreateLinkedInAppRequest(
	@field:Size(max = 80, message = "label must be at most 80 characters")
	val label: String? = null,
	@field:NotBlank(message = "clientId is required")
	@field:Pattern(
		regexp = "^[a-zA-Z0-9]{8,64}$",
		message = "clientId must be 8–64 alphanumeric characters",
	)
	val clientId: String,
	@field:NotBlank(message = "clientSecret is required")
	@field:Size(max = 512, message = "clientSecret must be at most 512 characters")
	val clientSecret: String,
)

data class UpdateLinkedInAppRequest(
	@field:Size(max = 80, message = "label must be at most 80 characters")
	val label: String? = null,
	@field:Pattern(
		regexp = "^(|[a-zA-Z0-9]{8,64})$",
		message = "clientId must be 8–64 alphanumeric characters",
	)
	val clientId: String? = null,
	val clientSecret: String? = null,
	val clearClientSecret: Boolean? = null,
)

data class LinkedInAppAuditEventResponse(
	val id: String?,
	val appId: String?,
	val appLabel: String?,
	val action: String,
	val summary: String,
	val actorEmail: String?,
	val createdAt: Instant,
)

data class LinkedInAppAuditListResponse(
	val events: List<LinkedInAppAuditEventResponse>,
	val total: Long,
	val page: Int,
	val size: Int,
)

data class LinkedInAppAuditExportResponse(
	val events: List<LinkedInAppAuditEventResponse>,
)

data class LinkedInAppHealthCheck(
	val code: String,
	val status: String,
	val message: String,
)

data class LinkedInAppHealthResponse(
	val ok: Boolean,
	val tokenExpiringSoon: Boolean = false,
	val checks: List<LinkedInAppHealthCheck>,
)

data class LinkedInAppVerifyResponse(
	val ok: Boolean,
	val appId: String,
	val message: String,
)
