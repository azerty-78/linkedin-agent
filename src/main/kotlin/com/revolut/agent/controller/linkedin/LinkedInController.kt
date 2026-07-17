package com.revolut.agent.controller.linkedin

import com.revolut.agent.dto.linkedin.LinkedInAccountListResponse
import com.revolut.agent.dto.linkedin.LinkedInAuthUrlResponse
import com.revolut.agent.dto.linkedin.LinkedInStatusResponse
import com.revolut.agent.dto.linkedin.SetOrganizationUrnRequest
import com.revolut.agent.dto.linkedin.UpdateLinkedInAccountRequest
import com.revolut.agent.service.linkedin.LinkedInOAuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
@RequestMapping("/api/linkedin")
class LinkedInController(
	private val linkedInOAuthService: LinkedInOAuthService,
) {

	/** Liste des comptes (max 3) + id actif. */
	@GetMapping("/accounts")
	fun listAccounts(): Mono<LinkedInAccountListResponse> =
		linkedInOAuthService.listAccounts()

	/**
	 * Démarre OAuth pour ajouter un nouveau compte.
	 * Query optionnelle : `label` (libellé UI).
	 */
	@PostMapping("/accounts/connect")
	fun connectNewAccount(
		@RequestParam(required = false) label: String?,
	): Mono<LinkedInAuthUrlResponse> =
		linkedInOAuthService.buildAuthorizationUrlForNewAccount(label)

	@GetMapping("/accounts/{id}")
	fun getAccount(@PathVariable id: String): Mono<LinkedInStatusResponse> =
		linkedInOAuthService.getAccount(id)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	/** Reconnexion OAuth d'un compte existant. */
	@GetMapping("/accounts/{id}/auth-url")
	fun reconnectAccount(@PathVariable id: String): Mono<LinkedInAuthUrlResponse> =
		linkedInOAuthService.buildAuthorizationUrlForAccount(id)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	@PutMapping("/accounts/{id}/organization-urn")
	fun setOrganizationUrn(
		@PathVariable id: String,
		@Valid @RequestBody request: SetOrganizationUrnRequest,
	): Mono<LinkedInStatusResponse> =
		linkedInOAuthService.setOrganizationUrn(id, request)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	@PutMapping("/accounts/{id}")
	fun updateAccount(
		@PathVariable id: String,
		@RequestBody request: UpdateLinkedInAccountRequest,
	): Mono<LinkedInStatusResponse> =
		linkedInOAuthService.updateAccount(id, request)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	@DeleteMapping("/accounts/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun deleteAccount(@PathVariable id: String): Mono<Void> =
		linkedInOAuthService.deleteAccount(id)
			.onErrorMap(NoSuchElementException::class.java) {
				ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
			}

	// ——— Compat legacy (compte actif) ———

	@GetMapping("/auth-url")
	fun authUrl(): Mono<LinkedInAuthUrlResponse> =
		linkedInOAuthService.buildAuthorizationUrl()

	@GetMapping("/status")
	fun status(): Mono<LinkedInStatusResponse> =
		linkedInOAuthService.getConnectionStatus()

	@PutMapping("/organization-urn")
	fun setActiveOrganizationUrn(
		@Valid @RequestBody request: SetOrganizationUrnRequest,
	): Mono<LinkedInStatusResponse> =
		linkedInOAuthService.resolveActiveAccount()
			.switchIfEmpty(
				Mono.error(
					ResponseStatusException(
						HttpStatus.UNPROCESSABLE_ENTITY,
						"Aucun compte LinkedIn actif",
					),
				),
			)
			.flatMap { account ->
				linkedInOAuthService.setOrganizationUrn(requireNotNull(account.id), request)
			}

	@DeleteMapping("/disconnect")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun disconnect(): Mono<Void> =
		linkedInOAuthService.disconnect()
}
