package com.revolut.agent.model.publication

enum class PublicationStatus {
	PENDING,
	IN_PROGRESS,
	/** Contenu généré, en attente de relecture humaine (`confirm`). */
	AWAITING_REVIEW,
	SUCCESS,
	PARTIAL_SUCCESS,
	FAILURE,
}
