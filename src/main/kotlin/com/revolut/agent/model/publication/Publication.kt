package com.revolut.agent.model.publication

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Run d'orchestration génération → LinkedIn → mail.
 * Distinct de [com.revolut.agent.model.linkedin.PublicationRecord] (trace API LinkedIn seule).
 *
 * Note: distinct de [com.revolut.agent.model.linkedin.PublicationStatus] (PENDING/PUBLISHED/FAILED).
 */
@Document(collection = "publications")
data class Publication(
	@Id
	val id: String? = null,
	val triggerType: PublicationTriggerType,
	val status: PublicationStatus = PublicationStatus.PENDING,
	val triggeredAt: Instant = Instant.now(),
	val completedAt: Instant? = null,
	val steps: List<PublicationStepResult> = emptyList(),
	val triggeredBy: String? = null,
)
