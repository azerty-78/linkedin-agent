package com.revolut.agent.controller

import com.revolut.agent.dto.GeneratedContentResponse
import com.revolut.agent.service.AiGenerationService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Endpoint de test manuel pour valider la génération LLM avant branchement du scheduler (phase 7).
 * Sera retiré ou protégé différemment plus tard.
 */
@RestController
@RequestMapping("/api/ai")
class AiTestController(
	private val aiGenerationService: AiGenerationService,
) {

	@PostMapping("/generate-test")
	fun generateTest(): Mono<GeneratedContentResponse> =
		aiGenerationService.generateDailyTip()
}
