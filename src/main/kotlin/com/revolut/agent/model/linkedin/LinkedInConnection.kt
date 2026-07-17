package com.revolut.agent.model.linkedin

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "linkedin_connection")
data class LinkedInConnection(
	@Id
	val id: String? = SINGLETON_ID,
	val status: LinkedInConnectionStatus = LinkedInConnectionStatus.NOT_CONNECTED,
	val encryptedAccessToken: String? = null,
	val accessTokenExpiresAt: Instant? = null,
	val linkedInUserUrn: String? = null,
	val linkedInOrganizationUrn: String? = null,
	val connectedAt: Instant? = null,
	val lastError: String? = null,
) {
	companion object {
		const val SINGLETON_ID = "singleton"
	}
}
