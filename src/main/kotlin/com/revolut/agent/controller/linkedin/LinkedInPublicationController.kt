package com.revolut.agent.controller.linkedin

import com.revolut.agent.dto.linkedin.PublicationRecordResponse
import com.revolut.agent.dto.linkedin.PublishRequest
import com.revolut.agent.service.linkedin.PublicationOrchestrationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Endpoints manuels de publication LinkedIn.
 * [POST /publish-test] est temporaire (même esprit que AiTestController) —
 * le scheduler phase 8 appellera [PublicationOrchestrationService] directement.
 */
@RestController
@RequestMapping("/api/linkedin")
class LinkedInPublicationController(
	private val publicationOrchestrationService: PublicationOrchestrationService,
) {

	@PostMapping("/publish-test")
	fun publishTest(
		@RequestBody(required = false) request: PublishRequest?,
	): Mono<PublicationRecordResponse> {
		val provided = request?.content?.trim()?.takeIf { it.isNotEmpty() }
		return if (provided != null) {
			publicationOrchestrationService.publishProvidedContent(provided)
		} else {
			publicationOrchestrationService.generateAndPublish()
		}
	}

	@GetMapping("/publications")
	fun listPublications(
		@RequestParam(defaultValue = "20") limit: Int,
	): Flux<PublicationRecordResponse> =
		publicationOrchestrationService.listPublications(limit)
}
