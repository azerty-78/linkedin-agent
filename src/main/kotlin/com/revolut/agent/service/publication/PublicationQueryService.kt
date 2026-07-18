package com.revolut.agent.service.publication

import com.revolut.agent.dto.publication.ConfirmPublicationResponse
import com.revolut.agent.dto.publication.PublicationListResponse
import com.revolut.agent.dto.publication.PublicationResponse
import com.revolut.agent.dto.publication.PublicationStepResponse
import com.revolut.agent.dto.publication.TriggerPublicationResponse
import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.model.publication.StepName
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.repository.publication.PublicationRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class PublicationQueryService(
	private val publicationRepository: PublicationRepository,
	private val publicationOrchestratorService: PublicationOrchestratorService,
) {

	fun triggerManual(
		adminEmail: String,
		llmProvider: LlmProvider? = null,
		awaitReview: Boolean = false,
	): Mono<TriggerPublicationResponse> =
		publicationOrchestratorService.orchestrate(
			triggerType = PublicationTriggerType.MANUAL,
			triggeredBy = adminEmail,
			llmProviderOverride = llmProvider,
			awaitReview = awaitReview,
		).map { pub ->
			TriggerPublicationResponse(
				publicationId = requireNotNull(pub.id),
				status = pub.status,
			)
		}

	fun confirm(
		publicationId: String,
		content: String,
	): Mono<ConfirmPublicationResponse> =
		publicationOrchestratorService.confirmPublication(publicationId, content)
			.map { pub ->
				ConfirmPublicationResponse(
					publicationId = requireNotNull(pub.id),
					status = pub.status,
				)
			}

	fun getById(id: String): Mono<PublicationResponse> =
		publicationRepository.findById(id)
			.switchIfEmpty(Mono.error(NoSuchElementException("Publication not found: $id")))
			.map(::toResponse)

	fun list(page: Int, size: Int): Mono<PublicationListResponse> {
		val safePage = page.coerceAtLeast(0)
		val safeSize = size.coerceIn(1, 100)
		val skip = (safePage.toLong() * safeSize)
		return publicationRepository.count()
			.zipWith(
				publicationRepository.findAllByOrderByTriggeredAtDesc()
					.skip(skip)
					.take(safeSize.toLong())
					.map(::toResponse)
					.collectList(),
			)
			.map { tuple ->
				PublicationListResponse(
					publications = tuple.t2,
					total = tuple.t1,
				)
			}
	}

	private fun toResponse(publication: Publication): PublicationResponse {
		val draft = publication.steps
			.firstOrNull { it.stepName == StepName.CONTENT_GENERATION }
			?.output
		return PublicationResponse(
			id = publication.id,
			triggerType = publication.triggerType,
			status = publication.status,
			triggeredAt = publication.triggeredAt,
			completedAt = publication.completedAt,
			steps = publication.steps.map { step ->
				PublicationStepResponse(
					stepName = step.stepName,
					status = step.status,
					startedAt = step.startedAt,
					completedAt = step.completedAt,
					errorMessage = step.errorMessage,
					output = step.output,
				)
			},
			triggeredBy = publication.triggeredBy,
			draftContent = draft,
			llmProvider = publication.llmProvider,
		)
	}
}
