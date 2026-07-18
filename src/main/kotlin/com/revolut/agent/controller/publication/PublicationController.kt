package com.revolut.agent.controller.publication

import com.revolut.agent.dto.publication.PublicationListResponse
import com.revolut.agent.dto.publication.PublicationResponse
import com.revolut.agent.dto.publication.TriggerPublicationRequest
import com.revolut.agent.dto.publication.TriggerPublicationResponse
import com.revolut.agent.service.publication.PublicationQueryService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/publications")
class PublicationController(
	private val publicationQueryService: PublicationQueryService,
) {

	@PostMapping("/trigger")
	fun trigger(
		authentication: Mono<Authentication>,
		@RequestBody(required = false) request: TriggerPublicationRequest?,
	): Mono<TriggerPublicationResponse> =
		authentication.flatMap { auth ->
			publicationQueryService.triggerManual(
				adminEmail = auth.name,
				llmProvider = request?.llmProvider,
			)
		}

	@GetMapping
	fun list(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): Mono<PublicationListResponse> =
		publicationQueryService.list(page, size)

	@GetMapping("/{id}")
	fun getById(@PathVariable id: String): Mono<PublicationResponse> =
		publicationQueryService.getById(id)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}
}
