package com.revolut.agent.model.publication

import java.time.Instant

data class PublicationStepResult(
	val stepName: StepName,
	val status: StepStatus = StepStatus.PENDING,
	val startedAt: Instant? = null,
	val completedAt: Instant? = null,
	val errorMessage: String? = null,
	/** Contenu généré (CONTENT_GENERATION) ou id post LinkedIn (LINKEDIN_PUBLISH). */
	val output: String? = null,
)
