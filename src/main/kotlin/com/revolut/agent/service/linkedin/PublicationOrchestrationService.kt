package com.revolut.agent.service.linkedin

import com.revolut.agent.dto.linkedin.PublicationRecordResponse
import com.revolut.agent.model.linkedin.PublicationRecord
import com.revolut.agent.repository.linkedin.PublicationRecordRepository
import com.revolut.agent.service.ai.AiGenerationService
import com.revolut.agent.service.settings.OrganizationSettingsService
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Orchestration légère génération IA → publication LinkedIn.
 * Le scheduler quotidien (phase 8) s'appuiera sur ces méthodes.
 */
@Service
class PublicationOrchestrationService(
	private val aiGenerationService: AiGenerationService,
	private val linkedInPublishingService: LinkedInPublishingService,
	private val publicationRecordRepository: PublicationRecordRepository,
	private val organizationSettingsService: OrganizationSettingsService,
) {

	fun generateAndPublish(): Mono<PublicationRecordResponse> =
		aiGenerationService.generateDailyTip()
			.flatMap { generated ->
				linkedInPublishingService.publish(
					content = generated.content,
					aiProvider = generated.provider,
				)
			}
			.map(::toResponse)

	/**
	 * Publie un texte fourni tel quel (relecture / édition admin).
	 * [aiProvider] = provider actif des settings (traçabilité uniquement).
	 */
	fun publishProvidedContent(content: String): Mono<PublicationRecordResponse> =
		organizationSettingsService.getSettings()
			.flatMap { settings ->
				linkedInPublishingService.publish(
					content = content,
					aiProvider = settings.activeLlmProvider,
				)
			}
			.map(::toResponse)

	fun listPublications(limit: Int): Flux<PublicationRecordResponse> {
		val safeLimit = limit.coerceIn(1, 100)
		return publicationRecordRepository.findAllByOrderByCreatedAtDesc()
			.take(safeLimit.toLong())
			.map(::toResponse)
	}

	private fun toResponse(record: PublicationRecord): PublicationRecordResponse =
		PublicationRecordResponse(
			id = record.id,
			content = record.content,
			aiProvider = record.aiProvider,
			targetUrn = record.targetUrn,
			linkedInPostId = record.linkedInPostId,
			status = record.status,
			errorMessage = record.errorMessage,
			createdAt = record.createdAt,
			publishedAt = record.publishedAt,
		)
}
