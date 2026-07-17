package com.revolut.agent.controller.linkedin

import com.revolut.agent.dto.linkedin.LinkedInAuthUrlResponse
import com.revolut.agent.dto.linkedin.LinkedInStatusResponse
import com.revolut.agent.dto.linkedin.SetOrganizationUrnRequest
import com.revolut.agent.service.linkedin.LinkedInOAuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/linkedin")
class LinkedInController(
	private val linkedInOAuthService: LinkedInOAuthService,
) {

	@GetMapping("/auth-url")
	fun authUrl(): LinkedInAuthUrlResponse =
		linkedInOAuthService.buildAuthorizationUrl()

	@GetMapping("/status")
	fun status(): Mono<LinkedInStatusResponse> =
		linkedInOAuthService.getConnectionStatus()

	@PutMapping("/organization-urn")
	fun setOrganizationUrn(
		@Valid @RequestBody request: SetOrganizationUrnRequest,
	): Mono<LinkedInStatusResponse> =
		linkedInOAuthService.setOrganizationUrn(request)

	@DeleteMapping("/disconnect")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun disconnect(): Mono<Void> =
		linkedInOAuthService.disconnect()
}
