package com.revolut.agent.controller.ai

import com.revolut.agent.dto.ai.GeneratedContentResponse
import com.revolut.agent.service.ai.AiGenerationService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Endpoint de test manuel — utilise toujours des modèles faibles / free (ai.test.*),
 * indépendamment des modèles premium choisis dans les settings.
 */
@RestController
@RequestMapping("/api/ai")
class AiTestController(
	private val aiGenerationService: AiGenerationService,
) {

	@PostMapping("/generate-test")
	fun generateTest(): Mono<GeneratedContentResponse> =
		aiGenerationService.generateDailyTipForTest()
}
