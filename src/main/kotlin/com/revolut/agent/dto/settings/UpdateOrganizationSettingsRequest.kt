package com.revolut.agent.dto.settings

import com.revolut.agent.model.settings.LlmProvider
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateOrganizationSettingsRequest(
	@field:NotBlank
	@field:Size(max = 200)
	val companyName: String,

	/**
	 * Slogan. `null` / `""` = vider ; valeur = remplacer.
	 */
	@field:Size(max = 120)
	val companyTagline: String? = null,

	@field:Size(max = 120)
	val industry: String? = null,

	/** Si non vide → URL absolue http/https (validée côté service). */
	@field:Size(max = 500)
	val websiteUrl: String? = null,

	@field:NotBlank
	val contentTopic: String,

	@field:Min(0)
	@field:Max(23)
	val publishHourUtc: Int,

	@field:NotBlank
	val timezone: String,

	val activeLlmProvider: LlmProvider,

	val openAiModel: String? = null,
	val anthropicModel: String? = null,
	val geminiModel: String? = null,

	val openAiApiKey: String? = null,
	val anthropicApiKey: String? = null,
	val geminiApiKey: String? = null,
	val linkedInOrganizationId: String? = null,

	/**
	 * Client ID LinkedIn Developer App.
	 * `null` = conserver ; `""` = effacer ; valeur = remplacer.
	 */
	val linkedInClientId: String? = null,
	/** Client secret — `null`/blank = conserver (ne jamais renvoyer le masqué). */
	val linkedInClientSecret: String? = null,
	/**
	 * Si `true`, efface le secret LinkedIn stocké (Delete).
	 * Indépendant de [linkedInClientSecret] pour ne pas casser le pattern UI « champ vide = garder ».
	 */
	val clearLinkedInClientSecret: Boolean? = null,
	/**
	 * Déprécié — redirect OAuth géré en `linkedin.redirect-uri` (serveur).
	 * Ignoré si présent (compat anciens clients).
	 */
	@Deprecated("Server-managed via linkedin.redirect-uri")
	val linkedInRedirectUri: String? = null,
	/**
	 * Déprécié — scopes gérés en `linkedin.oauth-scopes` (serveur).
	 * Ignoré si présent (compat anciens clients).
	 */
	@Deprecated("Server-managed via linkedin.oauth-scopes")
	val linkedInOauthScopes: String? = null,
	/** Compte LinkedIn à utiliser pour publier. Null = conserver ; `""` = aucun. */
	val activeLinkedInAccountId: String? = null,

	val schedulerEnabled: Boolean? = null,
)
