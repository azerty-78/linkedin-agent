package com.revolut.agent.service.publication

import com.revolut.agent.dto.publication.PublicationListResponse
import com.revolut.agent.dto.publication.PublicationResponse
import com.revolut.agent.dto.publication.PublicationStepResponse
import com.revolut.agent.dto.publication.TriggerPublicationResponse
import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.repository.publication.PublicationRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class PublicationQueryService(
	private val publicationRepository: PublicationRepository,
	private val publicationOrchestratorService: PublicationOrchestratorService,
) {

	fun triggerManual(adminEmail: String): Mono<TriggerPublicationResponse> =
		publicationOrchestratorService.orchestrate(
			triggerType = PublicationTriggerType.MANUAL,
			triggeredBy = adminEmail,
		).map { pub ->
			TriggerPublicationResponse(
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

	private fun toResponse(publication: Publication): PublicationResponse =
		PublicationResponse(
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
		)
}
