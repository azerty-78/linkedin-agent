package com.revolut.agent.dto.publication

import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.model.publication.StepName
import com.revolut.agent.model.publication.StepStatus
import java.time.Instant

data class PublicationStepResponse(
	val stepName: StepName,
	val status: StepStatus,
	val startedAt: Instant?,
	val completedAt: Instant?,
	val errorMessage: String?,
	val output: String?,
)

data class PublicationResponse(
	val id: String?,
	val triggerType: PublicationTriggerType,
	val status: PublicationStatus,
	val triggeredAt: Instant,
	val completedAt: Instant?,
	val steps: List<PublicationStepResponse>,
	val triggeredBy: String?,
)

data class PublicationListResponse(
	val publications: List<PublicationResponse>,
	val total: Long,
)

data class TriggerPublicationResponse(
	val publicationId: String,
	val status: PublicationStatus,
)
