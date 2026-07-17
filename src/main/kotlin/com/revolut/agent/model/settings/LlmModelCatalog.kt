package com.revolut.agent.model.settings

/**
 * Catalogue des modèles LLM proposés (style n8n) : listes pour UI + défauts tests.
 * Les IDs hors catalogue restent acceptés pour flexibilité (modèles custom / preview).
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
		// OpenAI — pas de vrai free tier API ; mini = le moins cher
		ModelOption("gpt-4o-mini", "GPT-4o Mini (économique)", LlmProvider.OPENAI, freeTier = false, recommendedForTests = true),
		ModelOption("gpt-4.1-mini", "GPT-4.1 Mini", LlmProvider.OPENAI, freeTier = false),
		ModelOption("gpt-4.1-nano", "GPT-4.1 Nano (très économique)", LlmProvider.OPENAI, freeTier = false, recommendedForTests = true),
		ModelOption("gpt-4o", "GPT-4o", LlmProvider.OPENAI, freeTier = false),

		// Anthropic — Haiku = tier économique
		ModelOption("claude-haiku-4-5", "Claude Haiku 4.5 (économique)", LlmProvider.ANTHROPIC, freeTier = false, recommendedForTests = true),
		ModelOption("claude-3-5-haiku-latest", "Claude 3.5 Haiku (latest)", LlmProvider.ANTHROPIC, freeTier = false, recommendedForTests = true),
		ModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6", LlmProvider.ANTHROPIC, freeTier = false),
		ModelOption("claude-opus-4-6", "Claude Opus 4.6", LlmProvider.ANTHROPIC, freeTier = false),

		// Gemini — free tier Google AI Studio
		ModelOption("gemini-2.0-flash", "Gemini 2.0 Flash (free tier)", LlmProvider.GEMINI, freeTier = true, recommendedForTests = true),
		ModelOption("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite (free tier)", LlmProvider.GEMINI, freeTier = true, recommendedForTests = true),
		ModelOption("gemini-1.5-flash", "Gemini 1.5 Flash (free tier)", LlmProvider.GEMINI, freeTier = true, recommendedForTests = true),
		ModelOption("gemini-1.5-pro", "Gemini 1.5 Pro", LlmProvider.GEMINI, freeTier = false),
	)

	fun defaultModel(provider: LlmProvider): String =
		when (provider) {
			LlmProvider.OPENAI -> "gpt-4o-mini"
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
