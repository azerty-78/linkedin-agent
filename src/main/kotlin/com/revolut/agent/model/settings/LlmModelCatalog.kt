package com.revolut.agent.model.settings

/**
 * Catalogue UI (choix utilisateur en settings).
 * Les tests (`/api/ai/generate-test`) n’utilisent PAS ces choix : voir `ai.test.*`.
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
		// OpenAI — choix libre (payant)
		ModelOption("gpt-4.1-nano", "GPT-4.1 Nano", LlmProvider.OPENAI, freeTier = false, recommendedForTests = true),
		ModelOption("gpt-4o-mini", "GPT-4o Mini", LlmProvider.OPENAI, freeTier = false),
		ModelOption("gpt-4.1-mini", "GPT-4.1 Mini", LlmProvider.OPENAI, freeTier = false),
		ModelOption("gpt-4o", "GPT-4o", LlmProvider.OPENAI, freeTier = false),

		// Anthropic — choix libre (payant)
		ModelOption("claude-haiku-4-5", "Claude Haiku 4.5", LlmProvider.ANTHROPIC, freeTier = false, recommendedForTests = true),
		ModelOption("claude-3-5-haiku-latest", "Claude 3.5 Haiku (latest)", LlmProvider.ANTHROPIC, freeTier = false),
		ModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6", LlmProvider.ANTHROPIC, freeTier = false),
		ModelOption("claude-opus-4-6", "Claude Opus 4.6", LlmProvider.ANTHROPIC, freeTier = false),

		// Gemini — free tier + modèles plus capables
		ModelOption("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite (FREE — idéal tests)", LlmProvider.GEMINI, freeTier = true, recommendedForTests = true),
		ModelOption("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite (FREE)", LlmProvider.GEMINI, freeTier = true, recommendedForTests = true),
		ModelOption("gemini-2.5-flash", "Gemini 2.5 Flash (FREE)", LlmProvider.GEMINI, freeTier = true),
		ModelOption("gemini-3.5-flash", "Gemini 3.5 Flash (FREE)", LlmProvider.GEMINI, freeTier = true),
	)

	private val shutdownGeminiModels = setOf(
		"gemini-2.0-flash",
		"gemini-2.0-flash-001",
		"gemini-2.0-flash-lite",
		"gemini-2.0-flash-lite-001",
		"gemini-1.5-flash",
		"gemini-1.5-pro",
	)

	fun defaultModel(provider: LlmProvider): String =
		when (provider) {
			LlmProvider.OPENAI -> "gpt-4o-mini"
			LlmProvider.ANTHROPIC -> "claude-haiku-4-5"
			LlmProvider.GEMINI -> "gemini-3.5-flash"
		}

	fun resolveGeminiModel(requested: String?): String {
		val id = requested?.trim().orEmpty()
		if (id.isBlank() || id.lowercase() in shutdownGeminiModels || id.lowercase().startsWith("gemini-2.0")) {
			return defaultModel(LlmProvider.GEMINI)
		}
		return id
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
