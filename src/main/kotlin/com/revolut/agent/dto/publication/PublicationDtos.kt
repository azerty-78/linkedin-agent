package com.revolut.agent.dto.publication

import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.model.publication.StepName
import com.revolut.agent.model.publication.StepStatus
import com.revolut.agent.model.settings.LlmProvider
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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
	/** Texte généré (copie de CONTENT_GENERATION.output) pour la relecture UI. */
	val draftContent: String? = null,
	val llmProvider: LlmProvider? = null,
	val scheduledPublishAt: Instant? = null,
)

data class PublicationListResponse(
	val publications: List<PublicationResponse>,
	val total: Long,
)

data class TriggerPublicationRequest(
	/** Override provider ; sinon `activeLlmProvider` des settings (scheduler / fallback). */
	val llmProvider: LlmProvider? = null,
	/**
	 * `true` = générer puis pause `AWAITING_REVIEW` (UI manuelle).
	 * `false` / omis = générer + publier LinkedIn (scheduler).
	 */
	val awaitReview: Boolean? = null,
)

data class TriggerPublicationResponse(
	val publicationId: String,
	val status: PublicationStatus,
)

data class ConfirmPublicationRequest(
	@field:NotBlank(message = "content is required")
	@field:Size(max = 3000, message = "content must be at most 3000 characters")
	val content: String,
)

data class ConfirmPublicationResponse(
	val publicationId: String,
	val status: PublicationStatus,
)

data class RegeneratePublicationRequest(
	val llmProvider: LlmProvider? = null,
)

data class SchedulePublicationRequest(
	@field:NotBlank(message = "content is required")
	@field:Size(max = 3000, message = "content must be at most 3000 characters")
	val content: String,
	val publishAt: Instant,
)

data class PublicationActionResponse(
	val publicationId: String,
	val status: PublicationStatus,
)

data class PublicationReadinessResponse(
	val ready: Boolean,
	val linkedInConnected: Boolean,
	val linkedInAccountLabel: String?,
	/** Auteur profil perso (`urn:li:person:…`) — cible publish par défaut. */
	val personUrn: String? = null,
	/** Page org — optionnel (Community Management plus tard). */
	val organizationUrn: String? = null,
	/** `PERSON` | `ORGANIZATION` | null */
	val publishTarget: String? = null,
	val activeAppConfigured: Boolean,
	val hasLlmKey: Boolean,
	val reasons: List<String>,
)
