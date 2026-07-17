package com.revolut.agent.model.linkedin

import com.revolut.agent.model.settings.LlmProvider
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "publication_records")
data class PublicationRecord(
	@Id
	val id: String? = null,
	val content: String,
	val aiProvider: LlmProvider,
	val targetUrn: String,
	val linkedInPostId: String? = null,
	val status: PublicationStatus = PublicationStatus.PENDING,
	val errorMessage: String? = null,
	val createdAt: Instant = Instant.now(),
	val publishedAt: Instant? = null,
)
