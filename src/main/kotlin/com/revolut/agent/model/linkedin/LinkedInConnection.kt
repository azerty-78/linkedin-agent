package com.revolut.agent.model.linkedin

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Compte LinkedIn connecté (Page / org). Max [MAX_ACCOUNTS] documents.
 * Les credentials d'app (client id/secret) sont dans [com.revolut.agent.model.settings.OrganizationSettings].
 */
@Document(collection = "linkedin_connection")
data class LinkedInConnection(
	@Id
	val id: String? = null,
	/** Libellé affiché côté UI (ex. "Page Kobe Prod"). */
	val label: String = "",
	val status: LinkedInConnectionStatus = LinkedInConnectionStatus.NOT_CONNECTED,
	val encryptedAccessToken: String? = null,
	val accessTokenExpiresAt: Instant? = null,
	val linkedInUserUrn: String? = null,
	val linkedInOrganizationUrn: String? = null,
	val connectedAt: Instant? = null,
	val lastError: String? = null,
	val createdAt: Instant = Instant.now(),
	val updatedAt: Instant = Instant.now(),
) {
	companion object {
		const val MAX_ACCOUNTS = 3
	}
}
