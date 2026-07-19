package com.revolut.agent.controller.settings

import com.revolut.agent.dto.settings.LlmModelsCatalogResponse
import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.dto.settings.UpdateOrganizationSettingsRequest
import com.revolut.agent.service.settings.OrganizationSettingsService
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/settings")
class OrganizationSettingsController(
	private val organizationSettingsService: OrganizationSettingsService,
) {

	@GetMapping
	fun getSettings(): Mono<OrganizationSettingsResponse> =
		organizationSettingsService.getSettings()

	@GetMapping("/llm-models")
	fun listLlmModels(): LlmModelsCatalogResponse =
		organizationSettingsService.listAvailableModels()

	@PutMapping
	fun updateSettings(
		@Valid @RequestBody request: UpdateOrganizationSettingsRequest,
		authentication: Mono<Authentication>,
	): Mono<OrganizationSettingsResponse> =
		authentication.flatMap { auth ->
			organizationSettingsService.updateSettings(
				request = request,
				updatedByEmail = auth.name,
			)
		}

	/**
	 * Mise à jour partielle (sections Paramètres).
	 * Body = sous-ensemble de champs ; omis = conserver.
	 */
	@PatchMapping
	fun patchSettings(
		@RequestBody body: JsonNode,
		authentication: Mono<Authentication>,
	): Mono<OrganizationSettingsResponse> =
		authentication.flatMap { auth ->
			organizationSettingsService.patchSettings(
				body = body,
				updatedByEmail = auth.name,
			)
		}
}
