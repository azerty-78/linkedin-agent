package com.revolut.agent.service.publication

import com.revolut.agent.exception.ai.AiGenerationException
import com.revolut.agent.exception.linkedin.LinkedInPublishingException
import com.revolut.agent.exception.publication.PublicationAlreadyRunningException
import com.revolut.agent.exception.publication.PublicationInvalidStateException
import com.revolut.agent.model.linkedin.PublicationRecord
import com.revolut.agent.model.linkedin.PublicationStatus as LinkedInRecordStatus
import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationStepResult
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.model.publication.StepName
import com.revolut.agent.model.publication.StepStatus
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.repository.publication.PublicationRepository
import com.revolut.agent.service.ai.AiGenerationService
import com.revolut.agent.service.linkedin.LinkedInPublishingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.Instant

@Service
class PublicationOrchestratorService(
	private val publicationRepository: PublicationRepository,
	private val aiGenerationService: AiGenerationService,
	private val linkedInPublishingService: LinkedInPublishingService,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun orchestrate(
		triggerType: PublicationTriggerType,
		triggeredBy: String?,
		llmProviderOverride: LlmProvider? = null,
		awaitReview: Boolean = false,
	): Mono<Publication> =
		publicationRepository.existsByStatus(PublicationStatus.IN_PROGRESS)
			.flatMap { alreadyRunning ->
				if (alreadyRunning) {
					Mono.error(PublicationAlreadyRunningException())
				} else {
					aiGenerationService.requireProviderReady(llmProviderOverride)
						.then(createInitialPublication(triggerType, triggeredBy))
				}
			}
			.flatMap { publication -> runPipeline(publication, llmProviderOverride, awaitReview) }

	/**
	 * Publie LinkedIn après relecture humaine.
	 * Prérequis : statut `AWAITING_REVIEW`.
	 */
	fun confirmPublication(publicationId: String, content: String): Mono<Publication> {
		val trimmed = content.trim()
		if (trimmed.isEmpty()) {
			return Mono.error(IllegalArgumentException("content must not be blank"))
		}
		if (trimmed.length > 3000) {
			return Mono.error(IllegalArgumentException("content must be at most 3000 characters"))
		}

		return publicationRepository.findById(publicationId)
			.switchIfEmpty(Mono.error(NoSuchElementException("Publication not found: $publicationId")))
			.flatMap { publication ->
				if (publication.status != PublicationStatus.AWAITING_REVIEW) {
					return@flatMap Mono.error(
						PublicationInvalidStateException(
							"Publication must be AWAITING_REVIEW to confirm (current=${publication.status})",
						),
					)
				}
				publicationRepository.existsByStatus(PublicationStatus.IN_PROGRESS)
					.flatMap { running ->
						if (running) {
							Mono.error(PublicationAlreadyRunningException())
						} else {
							val genStep = stepOf(publication, StepName.CONTENT_GENERATION)
							val updatedGen = genStep.copy(
								output = trimmed,
								completedAt = Instant.now(),
								status = StepStatus.SUCCESS,
							)
							val withContent = publication.copy(
								status = PublicationStatus.IN_PROGRESS,
								llmProvider = publication.llmProvider,
								steps = publication.steps.map {
									if (it.stepName == StepName.CONTENT_GENERATION) updatedGen else it
								},
							)
							publicationRepository.save(withContent)
						}
					}
			}
			.flatMap { inProgress ->
				val provider = inProgress.llmProvider ?: LlmProvider.GEMINI
				runLinkedInPublish(inProgress, trimmed, provider)
					.flatMap { afterLi ->
						runMailNotImplemented(afterLi)
							.flatMap { afterMail ->
								val liOk = stepOf(afterMail, StepName.LINKEDIN_PUBLISH).status == StepStatus.SUCCESS
								val global =
									if (liOk) PublicationStatus.SUCCESS else PublicationStatus.PARTIAL_SUCCESS
								finalizeRun(afterMail, global)
							}
					}
			}
	}

	private fun createInitialPublication(
		triggerType: PublicationTriggerType,
		triggeredBy: String?,
	): Mono<Publication> {
		val now = Instant.now()
		val initial = Publication(
			triggerType = triggerType,
			status = PublicationStatus.IN_PROGRESS,
			triggeredAt = now,
			triggeredBy = triggeredBy,
			steps = listOf(
				PublicationStepResult(stepName = StepName.CONTENT_GENERATION, status = StepStatus.PENDING),
				PublicationStepResult(stepName = StepName.LINKEDIN_PUBLISH, status = StepStatus.PENDING),
				PublicationStepResult(stepName = StepName.MAIL_NOTIFICATION, status = StepStatus.PENDING),
			),
		)
		return publicationRepository.save(initial)
			.doOnSuccess { pub ->
				log.info(
					"[Publication {}] created | trigger={} | by={}",
					pub?.id,
					triggerType,
					triggeredBy,
				)
			}
	}

	private fun runPipeline(
		publication: Publication,
		llmProviderOverride: LlmProvider?,
		awaitReview: Boolean,
	): Mono<Publication> {
		val id = requireNotNull(publication.id)
		return runContentGeneration(publication, llmProviderOverride)
			.flatMap { genResult ->
				if (genResult.publication.let { stepOf(it, StepName.CONTENT_GENERATION).status } != StepStatus.SUCCESS) {
					finalizeRun(genResult.publication, PublicationStatus.FAILURE)
				} else if (awaitReview) {
					pauseForReview(genResult.publication, genResult.provider!!)
				} else {
					runLinkedInPublish(genResult.publication, genResult.content!!, genResult.provider!!)
						.flatMap { afterLi ->
							runMailNotImplemented(afterLi)
								.flatMap { afterMail ->
									val liOk = stepOf(afterMail, StepName.LINKEDIN_PUBLISH).status == StepStatus.SUCCESS
									val global =
										if (liOk) PublicationStatus.SUCCESS else PublicationStatus.PARTIAL_SUCCESS
									finalizeRun(afterMail, global)
								}
						}
				}
			}
			.onErrorResume { ex ->
				log.error("[Publication {}] unexpected pipeline error: {}", id, ex.message)
				publicationRepository.findById(id)
					.flatMap { current ->
						finalizeRun(
							current.copy(steps = markRunningStepsFailed(current.steps, ex.message)),
							PublicationStatus.FAILURE,
						)
					}
					.switchIfEmpty(Mono.error(ex))
			}
	}

	private fun pauseForReview(publication: Publication, provider: LlmProvider): Mono<Publication> =
		publicationRepository.save(
			publication.copy(
				status = PublicationStatus.AWAITING_REVIEW,
				llmProvider = provider,
				completedAt = null,
			),
		).doOnSuccess { pub ->
			log.info(
				"[Publication {}] paused for review | status=AWAITING_REVIEW | provider={}",
				pub?.id,
				provider,
			)
		}

	private data class GenerationResult(
		val publication: Publication,
		val content: String? = null,
		val provider: LlmProvider? = null,
	)

	private fun runContentGeneration(
		publication: Publication,
		llmProviderOverride: LlmProvider?,
	): Mono<GenerationResult> {
		val id = publication.id
		val started = Instant.now()
		log.info(
			"[Publication {}] step CONTENT_GENERATION started | llmOverride={}",
			id,
			llmProviderOverride,
		)
		return saveStep(
			publication,
			StepName.CONTENT_GENERATION,
			PublicationStepResult(
				stepName = StepName.CONTENT_GENERATION,
				status = StepStatus.IN_PROGRESS,
				startedAt = started,
			),
		).flatMap { inProgress ->
			aiGenerationService.generateDailyTip(llmProviderOverride = llmProviderOverride)
				.flatMap { generated ->
					log.info(
						"[Publication {}] step CONTENT_GENERATION success | provider={} | length={}",
						id,
						generated.provider,
						generated.content.length,
					)
					saveStep(
						inProgress.copy(llmProvider = generated.provider),
						StepName.CONTENT_GENERATION,
						PublicationStepResult(
							stepName = StepName.CONTENT_GENERATION,
							status = StepStatus.SUCCESS,
							startedAt = started,
							completedAt = Instant.now(),
							output = generated.content,
						),
					).map { saved ->
						GenerationResult(saved, generated.content, generated.provider)
					}
				}
				.onErrorResume { ex ->
					val message = when (ex) {
						is AiGenerationException -> ex.message ?: "AI generation failed"
						else -> ex.message ?: "AI generation failed"
					}
					log.warn("[Publication {}] step CONTENT_GENERATION failure: {}", id, message)
					saveStep(
						inProgress,
						StepName.CONTENT_GENERATION,
						PublicationStepResult(
							stepName = StepName.CONTENT_GENERATION,
							status = StepStatus.FAILURE,
							startedAt = started,
							completedAt = Instant.now(),
							errorMessage = message,
						),
					).map { GenerationResult(it) }
				}
		}
	}

	private fun runLinkedInPublish(
		publication: Publication,
		content: String,
		provider: LlmProvider,
	): Mono<Publication> {
		val id = publication.id
		val started = Instant.now()
		log.info("[Publication {}] step LINKEDIN_PUBLISH started", id)
		return saveStep(
			publication,
			StepName.LINKEDIN_PUBLISH,
			PublicationStepResult(
				stepName = StepName.LINKEDIN_PUBLISH,
				status = StepStatus.IN_PROGRESS,
				startedAt = started,
			),
		).flatMap { inProgress ->
			publishWithRetry(content, provider)
				.flatMap { record ->
					if (record.status == LinkedInRecordStatus.PUBLISHED) {
						log.info(
							"[Publication {}] step LINKEDIN_PUBLISH success | postId={}",
							id,
							record.linkedInPostId,
						)
						saveStep(
							inProgress,
							StepName.LINKEDIN_PUBLISH,
							PublicationStepResult(
								stepName = StepName.LINKEDIN_PUBLISH,
								status = StepStatus.SUCCESS,
								startedAt = started,
								completedAt = Instant.now(),
								output = record.linkedInPostId,
							),
						)
					} else {
						val message = record.errorMessage ?: "LinkedIn publication failed"
						log.warn("[Publication {}] step LINKEDIN_PUBLISH failure: {}", id, message)
						saveStep(
							inProgress,
							StepName.LINKEDIN_PUBLISH,
							PublicationStepResult(
								stepName = StepName.LINKEDIN_PUBLISH,
								status = StepStatus.FAILURE,
								startedAt = started,
								completedAt = Instant.now(),
								errorMessage = message,
							),
						)
					}
				}
				.onErrorResume { ex ->
					val message = when (ex) {
						is LinkedInPublishingException -> ex.message ?: "LinkedIn publishing precondition failed"
						is TransientLinkedInPublishException -> ex.record.errorMessage ?: ex.message
						else -> ex.message ?: "LinkedIn publishing failed"
					}
					log.warn("[Publication {}] step LINKEDIN_PUBLISH failure: {}", id, message)
					saveStep(
						inProgress,
						StepName.LINKEDIN_PUBLISH,
						PublicationStepResult(
							stepName = StepName.LINKEDIN_PUBLISH,
							status = StepStatus.FAILURE,
							startedAt = started,
							completedAt = Instant.now(),
							errorMessage = message,
						),
					)
				}
		}
	}

	/**
	 * Retry backoff (2 retries, 2s) uniquement sur erreurs réseau / 5xx / rate-limit LinkedIn.
	 */
	private fun publishWithRetry(content: String, provider: LlmProvider): Mono<PublicationRecord> =
		Mono.defer {
			linkedInPublishingService.publish(content, provider)
				.flatMap { record ->
					if (
						record.status == LinkedInRecordStatus.FAILED &&
						isTransientLinkedInFailure(record.errorMessage)
					) {
						Mono.error(TransientLinkedInPublishException(record))
					} else {
						Mono.just(record)
					}
				}
		}
			.retryWhen(
				Retry.backoff(2, Duration.ofSeconds(2))
					.filter { it is TransientLinkedInPublishException }
					.doBeforeRetry { signal ->
						log.warn(
							"[Publication] LinkedIn transient failure — retry #{}: {}",
							signal.totalRetries() + 1,
							signal.failure().message,
						)
					},
			)
			.onErrorResume(TransientLinkedInPublishException::class.java) { Mono.just(it.record) }

	private fun runMailNotImplemented(publication: Publication): Mono<Publication> {
		val now = Instant.now()
		log.info("[Publication {}] step MAIL_NOTIFICATION NOT_IMPLEMENTED (phase 7)", publication.id)
		return saveStep(
			publication,
			StepName.MAIL_NOTIFICATION,
			PublicationStepResult(
				stepName = StepName.MAIL_NOTIFICATION,
				status = StepStatus.NOT_IMPLEMENTED,
				startedAt = now,
				completedAt = now,
				errorMessage = "En attente de la phase mail (phase 7)",
			),
		)
	}

	private fun finalizeRun(publication: Publication, status: PublicationStatus): Mono<Publication> =
		publicationRepository.save(
			publication.copy(
				status = status,
				completedAt = Instant.now(),
			),
		).doOnSuccess { pub ->
			log.info("[Publication {}] finished | status={}", pub?.id, status)
		}

	private fun saveStep(
		publication: Publication,
		stepName: StepName,
		step: PublicationStepResult,
	): Mono<Publication> {
		val updatedSteps = publication.steps.map { existing ->
			if (existing.stepName == stepName) step else existing
		}
		return publicationRepository.save(publication.copy(steps = updatedSteps))
	}

	private fun stepOf(publication: Publication, name: StepName): PublicationStepResult =
		publication.steps.first { it.stepName == name }

	private fun markRunningStepsFailed(
		steps: List<PublicationStepResult>,
		message: String?,
	): List<PublicationStepResult> =
		steps.map { step ->
			if (step.status == StepStatus.IN_PROGRESS) {
				step.copy(
					status = StepStatus.FAILURE,
					completedAt = Instant.now(),
					errorMessage = message ?: "Unexpected failure",
				)
			} else {
				step
			}
		}

	private fun isTransientLinkedInFailure(message: String?): Boolean {
		if (message.isNullOrBlank()) return false
		val m = message.lowercase()
		return m.contains("rate limit") ||
			m.contains("429") ||
			m.contains("erreur serveur linkedin") ||
			m.contains("http 5") ||
			m.contains("timeout") ||
			m.contains("connection")
	}
}

private class TransientLinkedInPublishException(
	val record: PublicationRecord,
) : RuntimeException(record.errorMessage)
