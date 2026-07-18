package com.revolut.agent.model.linkedin

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Application LinkedIn Developers (Client ID + Secret) — max [MAX_APPS] par organisation.
 * Distinct de [LinkedInConnection] (compte OAuth / Page).
 */
@Document(collection = "linkedin_apps")
@CompoundIndexes(
	CompoundIndex(name = "org_clientId_unique", def = "{'organizationId': 1, 'clientId': 1}", unique = true),
)
data class LinkedInApp(
	@Id
	val id: String? = null,
	val organizationId: String = DEFAULT_ORGANIZATION_ID,
	val label: String = "Default",
	val clientId: String,
	val encryptedClientSecret: String? = null,
	val isActive: Boolean = false,
	val createdAt: Instant = Instant.now(),
	val updatedAt: Instant = Instant.now(),
) {
	companion object {
		const val MAX_APPS = 3
		const val DEFAULT_ORGANIZATION_ID = "default"
	}
}
