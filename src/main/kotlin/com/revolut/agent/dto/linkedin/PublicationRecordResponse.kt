package com.revolut.agent.dto.linkedin

import com.revolut.agent.model.linkedin.PublicationStatus
import com.revolut.agent.model.settings.LlmProvider
import java.time.Instant

data class PublicationRecordResponse(
	val id: String?,
	val content: String,
	val aiProvider: LlmProvider,
	val targetUrn: String,
	val linkedInPostId: String?,
	val status: PublicationStatus,
	val errorMessage: String?,
	val createdAt: Instant,
	val publishedAt: Instant?,
	val accountId: String? = null,
	val accountLabel: String? = null,
	/** URL publique du post si dérivable (ex. feed/update/{urn}). */
	val linkedInPostUrl: String? = null,
)
