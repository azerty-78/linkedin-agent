package com.revolut.agent.service.ai

import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.model.settings.BrandTone
import com.revolut.agent.model.settings.ContentFormat
import com.revolut.agent.model.settings.ContentLanguage
import com.revolut.agent.model.settings.EmojiPolicy
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.PostLengthPreference
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.absoluteValue

/**
 * Unique builder for generate-test, publish-test, trigger manuel et scheduler.
 * Recharge toujours depuis les settings frais passés en argument (caller = GET DB).
 */
@Service
class PromptContextBuilder {

	fun build(
		settings: OrganizationSettingsResponse,
		provider: LlmProvider,
		model: String,
		compact: Boolean = false,
		useOrgBrief: Boolean = true,
	): ContentBrief {
		val language = resolveLanguage(settings)
		val formatChosen = if (useOrgBrief) chooseFormat(settings) else ContentFormat.TIP
		val targetLength = if (compact) {
			TargetLength(min = 80, max = 250)
		} else {
			lengthFor(settings.postLengthPreference)
		}
		val hashtags = if (useOrgBrief) parseHashtags(settings.defaultHashtags) else emptyList()
		val systemPrompt = if (useOrgBrief) {
			buildSystemPrompt(settings, language, formatChosen, targetLength, compact)
		} else {
			buildMinimalSystemPrompt(language, targetLength)
		}
		val userPrompt = if (useOrgBrief) {
			buildUserPrompt(settings, language, formatChosen, compact)
		} else {
			buildMinimalUserPrompt(settings, language)
		}

		return ContentBrief(
			systemPrompt = systemPrompt,
			userPrompt = userPrompt,
			language = language,
			formatChosen = formatChosen,
			targetLength = targetLength,
			hashtags = hashtags,
			provider = provider,
			model = model,
			emojiPolicyName = if (useOrgBrief) settings.emojiPolicy.name else EmojiPolicy.SPARSE.name,
		)
	}

	private fun buildMinimalSystemPrompt(language: String, targetLength: TargetLength): String =
		"""
			Tu rédiges un post LinkedIn court, prêt à publier.

			RÈGLES
			- Un seul post LinkedIn, sans markdown de titre
			- Pas de meta-commentaire — uniquement le texte du post
			- Langue: $language
			- Longueur: ${targetLength.min}-${targetLength.max} caractères
			- Mode test minimal: pas de brief entreprise, seulement le sujet demandé
		""".trimIndent()

	private fun buildMinimalUserPrompt(
		settings: OrganizationSettingsResponse,
		language: String,
	): String {
		val topic = settings.contentTopic.trim().ifBlank { "astuces professionnelles" }
		return "Rédige maintenant un tip LinkedIn ultra-court sur: \"$topic\" (langue: $language)."
	}

	private fun buildSystemPrompt(
		settings: OrganizationSettingsResponse,
		language: String,
		formatChosen: ContentFormat?,
		targetLength: TargetLength,
		compact: Boolean,
	): String {
		val toneHint = toneInstruction(settings.brandTone)
		val emojiHint = when (settings.emojiPolicy) {
			EmojiPolicy.NONE -> "aucune (interdit)"
			EmojiPolicy.SPARSE -> "parcimonie (0–2 max)"
			EmojiPolicy.ALLOWED -> "autorisés si naturels"
		}
		val formatLine = formatChosen?.let { "Format demandé: ${formatLabel(it)}" }
			?: "Format: libre (tip actionnable recommandé)"

		val identity = buildString {
			appendLine("IDENTITÉ")
			appendLine("- Entreprise: ${dash(settings.companyName.ifBlank { "—" })}")
			appendLine("- Slogan: ${dash(settings.companyTagline)}")
			appendLine("- Secteur: ${dash(settings.industry)}")
			appendLine("- Site: ${dash(settings.websiteUrl)}")
		}.trimEnd()

		val brief = buildString {
			appendLine("BRIEF")
			appendLine("- Sujet principal: ${dash(settings.contentTopic.ifBlank { "astuces professionnelles" })}")
			appendLine("- Public: ${dash(settings.targetAudience)}")
			appendLine("- Objectifs: ${dash(settings.contentGoals)}")
			appendLine("- Piliers: ${dash(settings.contentPillars)}")
			appendLine("- À éviter: ${dash(settings.topicsToAvoid)}")
			appendLine("- Mots-clés à inclure si naturel: ${dash(settings.mustIncludeKeywords)}")
		}.trimEnd()

		val style = buildString {
			appendLine("STYLE")
			appendLine("- Langue: $language")
			appendLine("- Ton: ${settings.brandTone.name} — $toneHint")
			appendLine("- $formatLine")
			appendLine("- Longueur: ${targetLength.min}-${targetLength.max} caractères")
			appendLine("- Emojis: $emojiHint")
			appendLine("- CTA: ${dash(settings.callToActionPreference)}")
			appendLine("- Consignes prioritaires: ${dash(settings.brandGuidelines)}")
			appendLine("- Exemple de style (imiter sans copier): ${dash(settings.examplePostStyle)}")
		}.trimEnd()

		val rules = buildString {
			appendLine("RÈGLES")
			appendLine("- Un seul post LinkedIn, prêt à publier, sans markdown de titre")
			appendLine("- Pas de sujets interdits listés ci-dessus")
			appendLine("- Pas de meta-commentaire — uniquement le texte du post")
			if (compact) {
				appendLine("- Mode test: reste très court (≤ ${targetLength.max} caractères)")
			}
			appendLine("- Si hashtags absents du texte et defaultHashtags présents: le backend les ajoutera")
			appendLine("- Priorité des instructions: brandGuidelines > examplePostStyle > ton/langue/format/longueur > contentTopic")
		}.trimEnd()

		return """
			Tu rédiges un post LinkedIn pour la Page d'entreprise.

			$identity

			$brief

			$style

			$rules
		""".trimIndent()
	}

	private fun buildUserPrompt(
		settings: OrganizationSettingsResponse,
		language: String,
		formatChosen: ContentFormat?,
		compact: Boolean,
	): String {
		val topic = settings.contentTopic.trim().ifBlank { "astuces professionnelles" }
		val formatBit = formatChosen?.let { " au format ${formatLabel(it)}" }.orEmpty()
		val lengthBit = if (compact) "ultra-court" else "complet"
		return "Rédige maintenant le post LinkedIn $lengthBit$formatBit sur: \"$topic\" (langue: $language)."
	}

	fun resolveLanguage(settings: OrganizationSettingsResponse): String =
		when (settings.contentLanguage) {
			ContentLanguage.fr -> "fr"
			ContentLanguage.en -> "en"
			ContentLanguage.auto -> detectLanguage(settings)
		}

	fun chooseFormat(settings: OrganizationSettingsResponse): ContentFormat? {
		val formats = settings.preferredFormats
		if (formats.isEmpty()) {
			return ContentFormat.TIP
		}
		val seed = "${settings.companyName}|${LocalDate.now(ZoneOffset.UTC)}"
		val index = seed.hashCode().absoluteValue % formats.size
		return formats[index]
	}

	fun lengthFor(pref: PostLengthPreference): TargetLength =
		when (pref) {
			PostLengthPreference.SHORT -> TargetLength(500, 900)
			PostLengthPreference.MEDIUM -> TargetLength(900, 1500)
			PostLengthPreference.LONG -> TargetLength(1500, 2200)
		}

	fun parseHashtags(raw: String?): List<String> {
		if (raw.isNullOrBlank()) {
			return emptyList()
		}
		return raw.split(Regex("[\\s,;]+"))
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.map { if (it.startsWith("#")) it else "#$it" }
			.distinct()
	}

	private fun detectLanguage(settings: OrganizationSettingsResponse): String {
		val sample = listOfNotNull(
			settings.contentTopic,
			settings.brandGuidelines,
			settings.targetAudience,
		).joinToString(" ")
		if (sample.isBlank()) {
			return "fr"
		}
		val lower = sample.lowercase()
		val hasFrenchHints = sample.any { it in "àâäéèêëïîôùûüçÀÂÄÉÈÊËÏÎÔÙÛÜÇ" } ||
			listOf(" le ", " la ", " les ", " des ", " une ", " pour ", " avec ", " dans ").any { lower.contains(it) }
		return if (hasFrenchHints) "fr" else "en"
	}

	private fun toneInstruction(tone: BrandTone): String =
		when (tone) {
			BrandTone.PROFESSIONAL -> "Formel, corporate, clair"
			BrandTone.CASUAL -> "Conversationnel, accessible"
			BrandTone.EXPERT -> "Technique, crédible, précis"
			BrandTone.INSPIRATIONAL -> "Motivant, storytelling"
			BrandTone.EDUCATIONAL -> "Pédagogique, tips actionnables"
		}

	private fun formatLabel(format: ContentFormat): String =
		when (format) {
			ContentFormat.TIP -> "TIP (conseil actionnable)"
			ContentFormat.STORY -> "STORY (anecdote)"
			ContentFormat.QUESTION -> "QUESTION (engagement)"
			ContentFormat.LIST -> "LIST (liste à puces)"
			ContentFormat.OPINION -> "OPINION (prise de position)"
			ContentFormat.CASE_STUDY -> "CASE_STUDY (retour d'expérience)"
		}

	private fun dash(value: String?): String =
		value?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
}
