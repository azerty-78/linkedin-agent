package com.revolut.agent.model.publication

enum class PublicationStatus {
	PENDING,
	IN_PROGRESS,
	/** Contenu généré, en attente de relecture humaine (`confirm`). */
	AWAITING_REVIEW,
	/** One-shot planifié : publication LinkedIn à `scheduledPublishAt`. */
	SCHEDULED,
	CANCELLED,
	SUCCESS,
	PARTIAL_SUCCESS,
	FAILURE,
}

/** Statuts qui bloquent un nouveau trigger (verrou 409). */
val ACTIVE_PUBLICATION_STATUSES: Set<PublicationStatus> = setOf(
	PublicationStatus.IN_PROGRESS,
	PublicationStatus.AWAITING_REVIEW,
	PublicationStatus.SCHEDULED,
)
