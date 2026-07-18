package com.revolut.agent.controller.publication

import com.revolut.agent.dto.publication.ConfirmPublicationRequest
import com.revolut.agent.dto.publication.ConfirmPublicationResponse
import com.revolut.agent.dto.publication.PublicationActionResponse
import com.revolut.agent.dto.publication.PublicationListResponse
import com.revolut.agent.dto.publication.PublicationReadinessResponse
import com.revolut.agent.dto.publication.PublicationResponse
import com.revolut.agent.dto.publication.RegeneratePublicationRequest
import com.revolut.agent.dto.publication.SchedulePublicationRequest
import com.revolut.agent.dto.publication.TriggerPublicationRequest
import com.revolut.agent.dto.publication.TriggerPublicationResponse
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationTriggerType
import com.revolut.agent.service.publication.PublicationQueryService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
import java.time.Instant

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
				awaitReview = request?.awaitReview == true,
			)
		}

	@PostMapping("/{id}/confirm")
	fun confirm(
		@PathVariable id: String,
		@Valid @RequestBody body: ConfirmPublicationRequest,
	): Mono<ConfirmPublicationResponse> =
		publicationQueryService.confirm(id, body.content)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	@PostMapping("/{id}/cancel")
	fun cancel(@PathVariable id: String): Mono<PublicationActionResponse> =
		publicationQueryService.cancel(id)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	@PostMapping("/{id}/regenerate")
	fun regenerate(
		@PathVariable id: String,
		@RequestBody(required = false) body: RegeneratePublicationRequest?,
	): Mono<PublicationActionResponse> =
		publicationQueryService.regenerate(id, body?.llmProvider)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	@PostMapping("/{id}/schedule")
	fun schedule(
		@PathVariable id: String,
		@Valid @RequestBody body: SchedulePublicationRequest,
	): Mono<PublicationActionResponse> =
		publicationQueryService.schedule(id, body.content, body.publishAt)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	@GetMapping("/readiness")
	fun readiness(): Mono<PublicationReadinessResponse> =
		publicationQueryService.readiness()

	@GetMapping("/export")
	fun export(
		@RequestParam(defaultValue = "csv") format: String,
		@RequestParam(required = false) status: PublicationStatus?,
		@RequestParam(required = false) triggerType: PublicationTriggerType?,
		@RequestParam(required = false) from: Instant?,
		@RequestParam(required = false) to: Instant?,
	): Mono<ResponseEntity<*>> =
		publicationQueryService.exportRows(status, triggerType, from, to)
			.map { rows ->
				when (format.lowercase()) {
					"json" -> ResponseEntity.ok()
						.contentType(MediaType.APPLICATION_JSON)
						.body(rows)
					"csv" -> ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"publications.csv\"")
						.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
						.body(publicationQueryService.toCsv(rows))
					else -> throw ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"format must be csv or json",
					)
				}
			}

	@GetMapping
	fun list(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: PublicationStatus?,
		@RequestParam(required = false) triggerType: PublicationTriggerType?,
		@RequestParam(required = false) from: Instant?,
		@RequestParam(required = false) to: Instant?,
	): Mono<PublicationListResponse> =
		publicationQueryService.list(page, size, status, triggerType, from, to)

	@GetMapping("/{id}")
	fun getById(@PathVariable id: String): Mono<PublicationResponse> =
		publicationQueryService.getById(id)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}
}
