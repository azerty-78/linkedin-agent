package com.revolut.agent.service.publication

import com.revolut.agent.dto.publication.ConfirmPublicationResponse
import com.revolut.agent.dto.publication.PublicationActionResponse
import com.revolut.agent.dto.publication.PublicationListResponse
import com.revolut.agent.dto.publication.PublicationResponse
import com.revolut.agent.dto.publication.PublicationStepResponse
import com.revolut.agent.dto.publication.TriggerPublicationResponse
import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.model.publication.StepName
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.repository.publication.PublicationCriteriaRepository
import com.revolut.agent.repository.publication.PublicationRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class PublicationQueryService(
	private val publicationRepository: PublicationRepository,
	private val publicationCriteriaRepository: PublicationCriteriaRepository,
	private val publicationOrchestratorService: PublicationOrchestratorService,
	private val publicationReadinessService: PublicationReadinessService,
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

	fun cancel(publicationId: String): Mono<PublicationActionResponse> =
		publicationOrchestratorService.cancelPublication(publicationId)
			.map { pub ->
				PublicationActionResponse(
					publicationId = requireNotNull(pub.id),
					status = pub.status,
				)
			}

	fun regenerate(
		publicationId: String,
		llmProvider: LlmProvider? = null,
	): Mono<PublicationActionResponse> =
		publicationOrchestratorService.regeneratePublication(publicationId, llmProvider)
			.map { pub ->
				PublicationActionResponse(
					publicationId = requireNotNull(pub.id),
					status = pub.status,
				)
			}

	fun schedule(
		publicationId: String,
		content: String,
		publishAt: Instant,
	): Mono<PublicationActionResponse> =
		publicationOrchestratorService.schedulePublication(publicationId, content, publishAt)
			.map { pub ->
				PublicationActionResponse(
					publicationId = requireNotNull(pub.id),
					status = pub.status,
				)
			}

	fun readiness() = publicationReadinessService.getReadiness()

	fun getById(id: String): Mono<PublicationResponse> =
		publicationRepository.findById(id)
			.switchIfEmpty(Mono.error(NoSuchElementException("Publication not found: $id")))
			.map(::toResponse)

	fun list(
		page: Int,
		size: Int,
		status: PublicationStatus? = null,
		triggerType: PublicationTriggerType? = null,
		from: Instant? = null,
		to: Instant? = null,
	): Mono<PublicationListResponse> {
		val safePage = page.coerceAtLeast(0)
		val safeSize = size.coerceIn(1, 100)
		val skip = safePage.toLong() * safeSize
		return publicationCriteriaRepository.countFiltered(status, triggerType, from, to)
			.zipWith(
				publicationCriteriaRepository.findFiltered(status, triggerType, from, to, skip, safeSize.toLong())
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

	fun exportRows(
		status: PublicationStatus? = null,
		triggerType: PublicationTriggerType? = null,
		from: Instant? = null,
		to: Instant? = null,
	): Mono<List<PublicationExportRow>> =
		publicationCriteriaRepository.findAllFiltered(status, triggerType, from, to)
			.map(::toExportRow)
			.collectList()

	fun toCsv(rows: List<PublicationExportRow>): String {
		val header = "id,triggeredAt,completedAt,triggerType,status,triggeredBy,contentExcerpt,linkedInPostId"
		val body = rows.joinToString("\n") { row ->
			listOf(
				row.id,
				row.triggeredAt,
				row.completedAt.orEmpty(),
				row.triggerType,
				row.status,
				row.triggeredBy.orEmpty(),
				csvEscape(row.contentExcerpt.orEmpty()),
				row.linkedInPostId.orEmpty(),
			).joinToString(",")
		}
		return if (body.isEmpty()) header else "$header\n$body"
	}

	private fun toExportRow(publication: Publication): PublicationExportRow {
		val draft = publication.steps
			.firstOrNull { it.stepName == StepName.CONTENT_GENERATION }
			?.output
		val postId = publication.steps
			.firstOrNull { it.stepName == StepName.LINKEDIN_PUBLISH }
			?.output
		return PublicationExportRow(
			id = publication.id.orEmpty(),
			triggeredAt = publication.triggeredAt.toString(),
			completedAt = publication.completedAt?.toString(),
			triggerType = publication.triggerType.name,
			status = publication.status.name,
			triggeredBy = publication.triggeredBy,
			contentExcerpt = draft?.take(200),
			linkedInPostId = postId,
		)
	}

	private fun csvEscape(value: String): String {
		val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
		val escaped = value.replace("\"", "\"\"")
		return if (needsQuotes) "\"$escaped\"" else escaped
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
			scheduledPublishAt = publication.scheduledPublishAt,
		)
	}
}

data class PublicationExportRow(
	val id: String,
	val triggeredAt: String,
	val completedAt: String?,
	val triggerType: String,
	val status: String,
	val triggeredBy: String?,
	val contentExcerpt: String?,
	val linkedInPostId: String?,
)
