package com.revolut.agent.controller.linkedin

import com.revolut.agent.dto.linkedin.CreateLinkedInAppRequest
import com.revolut.agent.dto.linkedin.LinkedInAppAuditExportResponse
import com.revolut.agent.dto.linkedin.LinkedInAppAuditListResponse
import com.revolut.agent.dto.linkedin.LinkedInAppHealthResponse
import com.revolut.agent.dto.linkedin.LinkedInAppListResponse
import com.revolut.agent.dto.linkedin.LinkedInAppVerifyResponse
import com.revolut.agent.dto.linkedin.UpdateLinkedInAppRequest
import com.revolut.agent.service.linkedin.LinkedInAppService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/linkedin/apps")
class LinkedInAppController(
	private val linkedInAppService: LinkedInAppService,
) {

	@GetMapping
	fun list(): Mono<LinkedInAppListResponse> =
		linkedInAppService.listApps()

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(
		authentication: Mono<Authentication>,
		@Valid @RequestBody body: CreateLinkedInAppRequest,
	): Mono<LinkedInAppListResponse> =
		authentication.flatMap { auth ->
			linkedInAppService.createApp(body, actorEmail = auth.name)
		}

	@PutMapping("/{id}")
	fun update(
		authentication: Mono<Authentication>,
		@PathVariable id: String,
		@Valid @RequestBody body: UpdateLinkedInAppRequest,
	): Mono<LinkedInAppListResponse> =
		authentication.flatMap { auth ->
			linkedInAppService.updateApp(id, body, actorEmail = auth.name)
		}.onErrorMap(NoSuchElementException::class.java) {
			ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
		}

	@PutMapping("/{id}/activate")
	fun activate(
		authentication: Mono<Authentication>,
		@PathVariable id: String,
	): Mono<LinkedInAppListResponse> =
		authentication.flatMap { auth ->
			linkedInAppService.activateApp(id, actorEmail = auth.name)
		}.onErrorMap(NoSuchElementException::class.java) {
			ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
		}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(
		authentication: Mono<Authentication>,
		@PathVariable id: String,
	): Mono<Void> =
		authentication.flatMap { auth ->
			linkedInAppService.deleteApp(id, actorEmail = auth.name)
		}.onErrorMap(NoSuchElementException::class.java) {
			ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
		}

	@GetMapping("/audit")
	fun audit(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "5") size: Int,
	): Mono<LinkedInAppAuditListResponse> =
		linkedInAppService.listAudit(page, size)

	@GetMapping("/audit/export")
	fun auditExport(): Mono<LinkedInAppAuditExportResponse> =
		linkedInAppService.exportAudit()

	@GetMapping("/health")
	fun health(): Mono<LinkedInAppHealthResponse> =
		linkedInAppService.health()

	@PostMapping("/{id}/verify")
	fun verify(@PathVariable id: String): Mono<LinkedInAppVerifyResponse> =
		linkedInAppService.verifyApp(id)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}
}
