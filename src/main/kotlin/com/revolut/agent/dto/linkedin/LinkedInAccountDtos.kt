package com.revolut.agent.dto.linkedin

import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import java.time.Instant

data class LinkedInStatusResponse(
	val id: String?,
	val label: String,
	val status: LinkedInConnectionStatus,
	val linkedInUserUrn: String?,
	val linkedInOrganizationUrn: String?,
	val connectedAt: Instant?,
	val lastError: String?,
	val isActive: Boolean = false,
	/** Nom de la Page LinkedIn (si scopes org OK), sinon null. */
	val organizationName: String? = null,
	val organizationLogoUrl: String? = null,
	val organizationVanityName: String? = null,
	/** Credentials app org (copie) pour tableau autonome — secret toujours masqué. */
	val linkedInClientId: String? = null,
	val linkedInClientSecretMasked: String? = null,
)

data class LinkedInAccountListResponse(
	val accounts: List<LinkedInStatusResponse>,
	val maxAccounts: Int,
	val activeLinkedInAccountId: String?,
)

/**
 * Résumé compact pour dashboards / page Test.
 * Codes [publishBlockers] stables pour i18n front.
 */
data class LinkedInSummaryResponse(
	val hasAppCredentials: Boolean,
	val hasActiveApp: Boolean = false,
	val activeAppId: String? = null,
	val appsCount: Int = 0,
	val accountsCount: Int,
	val maxAccounts: Int,
	val activeAccount: LinkedInStatusResponse?,
	val canPublish: Boolean,
	val publishBlockers: List<String>,
)

data class UpdateLinkedInAccountRequest(
	val label: String? = null,
)

data class LinkedInAuthUrlResponse(
	val authorizationUrl: String,
	/** Id du compte en cours de (re)connexion ; null si nouveau compte. */
	val accountId: String? = null,
)

/** Codes machine pour [LinkedInSummaryResponse.publishBlockers]. */
object LinkedInPublishBlockers {
	const val MISSING_CLIENT_ID = "MISSING_CLIENT_ID"
	const val MISSING_CLIENT_SECRET = "MISSING_CLIENT_SECRET"
	const val NO_ACTIVE_APP = "NO_ACTIVE_APP"
	const val NO_ACTIVE_ACCOUNT = "NO_ACTIVE_ACCOUNT"
	const val NOT_CONNECTED = "NOT_CONNECTED"
	/** Profil perso requis pour publier (priorité actuelle). */
	const val MISSING_PERSON_URN = "MISSING_PERSON_URN"
	/** Page org — optionnel ; ne bloque plus si person URN présent. */
	@Deprecated("Page org optionnelle — préférer MISSING_PERSON_URN")
	const val MISSING_ORG_URN = "MISSING_ORG_URN"
	const val EXPIRED = "EXPIRED"
	const val ERROR_STATUS = "ERROR_STATUS"
}
