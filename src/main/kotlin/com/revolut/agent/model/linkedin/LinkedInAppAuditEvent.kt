package com.revolut.agent.model.linkedin

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "linkedin_app_audit")
data class LinkedInAppAuditEvent(
	@Id
	val id: String? = null,
	val organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	val appId: String? = null,
	val appLabel: String? = null,
	val action: LinkedInAppAuditAction,
	val summary: String,
	val actorEmail: String? = null,
	val changedFields: List<String> = emptyList(),
	val createdAt: Instant = Instant.now(),
)
