package com.revolut.agent.dto.ai

import com.revolut.agent.model.settings.BrandTone
import com.revolut.agent.model.settings.ContentLanguage
import com.revolut.agent.model.settings.LlmProvider

/**
 * Body optionnel de `POST /api/ai/generate-test`.
 * Tous les champs sont optionnels — body absent / `{}` = comportement historique.
 */
data class GenerateTestRequest(
	val llmProvider: LlmProvider? = null,
	val topic: String? = null,
	val brandTone: BrandTone? = null,
	val contentLanguage: ContentLanguage? = null,
	/** `false` = tip minimal (topic + règles LinkedIn), sans brief org complet. */
	val useOrgBrief: Boolean? = null,
)
