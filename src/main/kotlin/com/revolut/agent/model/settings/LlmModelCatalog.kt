package com.revolut.agent.model.settings

/**
 * Catalogue des modèles LLM proposés (style n8n).
 * Important : OpenAI / Anthropic API = payant. Seul Gemini Flash est free-tier.
 */
object LlmModelCatalog {

	data class ModelOption(
		val id: String,
		val label: String,
		val provider: LlmProvider,
		val freeTier: Boolean,
		val recommendedForTests: Boolean = false,
	)

	val models: List<ModelOption> = listOf(
		// OpenAI — AUCUN modèle API gratuit (nécessite crédit billing)
		ModelOption("gpt-4.1-nano", "GPT-4.1 Nano (moins cher — pas gratuit)", LlmProvider.OPENAI, freeTier = false, recommendedForTests = true),
		ModelOption("gpt-4o-mini", "GPT-4o Mini (économique — pas gratuit)", LlmProvider.OPENAI, freeTier = false, recommendedForTests = true),
		ModelOption("gpt-4.1-mini", "GPT-4.1 Mini", LlmProvider.OPENAI, freeTier = false),
		ModelOption("gpt-4o", "GPT-4o (coûteux)", LlmProvider.OPENAI, freeTier = false),

		// Anthropic — payant ; Haiku = le moins cher
		ModelOption("claude-haiku-4-5", "Claude Haiku 4.5 (économique — pas gratuit)", LlmProvider.ANTHROPIC, freeTier = false, recommendedForTests = true),
		ModelOption("claude-3-5-haiku-latest", "Claude 3.5 Haiku (latest)", LlmProvider.ANTHROPIC, freeTier = false),
		ModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6 (coûteux)", LlmProvider.ANTHROPIC, freeTier = false),

		// Gemini — free tier Google AI Studio
		ModelOption("gemini-2.0-flash", "Gemini 2.0 Flash (FREE tier)", LlmProvider.GEMINI, freeTier = true, recommendedForTests = true),
		ModelOption("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite (FREE tier)", LlmProvider.GEMINI, freeTier = true, recommendedForTests = true),
		ModelOption("gemini-1.5-flash", "Gemini 1.5 Flash (FREE tier)", LlmProvider.GEMINI, freeTier = true, recommendedForTests = true),
		ModelOption("gemini-1.5-pro", "Gemini 1.5 Pro", LlmProvider.GEMINI, freeTier = false),
	)

	fun defaultModel(provider: LlmProvider): String =
		when (provider) {
			LlmProvider.OPENAI -> "gpt-4.1-nano"
			LlmProvider.ANTHROPIC -> "claude-haiku-4-5"
			LlmProvider.GEMINI -> "gemini-2.0-flash"
		}

	fun forProvider(provider: LlmProvider): List<ModelOption> =
		models.filter { it.provider == provider }

	fun recommendedTestModel(provider: LlmProvider): String =
		forProvider(provider)
			.firstOrNull { it.recommendedForTests && it.freeTier }
			?.id
			?: forProvider(provider).firstOrNull { it.recommendedForTests }?.id
			?: defaultModel(provider)
}
