package com.revolut.agent.dto.linkedin

/**
 * Body optionnel pour POST /api/linkedin/publish-test.
 * - content absent / blank → génération IA puis publication
 * - content fourni → publication telle quelle (relecture / édition admin)
 */
data class PublishRequest(
	val content: String? = null,
)
