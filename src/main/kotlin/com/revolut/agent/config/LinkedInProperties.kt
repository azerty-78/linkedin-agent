package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Config LinkedIn non secrète — 100 % serveur (pas d’UI settings).
 * Client ID / secret → LinkedInApp (DB chiffrée).
 *
 * Properties :
 * - `linkedin.redirect-uri`
 * - `linkedin.oauth-scopes`
 */
@ConfigurationProperties(prefix = "linkedin")
class LinkedInProperties {

	/** Callback OAuth backend, ex. https://api…/api/linkedin/callback */
	var redirectUri: String = "http://localhost:8080/api/linkedin/callback"

	/**
	 * Scopes OAuth — profil perso (Share on LinkedIn).
	 * Community Management / `w_organization_social` = plus tard.
	 */
	var oauthScopes: String = "openid profile email w_member_social"

	var apiBaseUrl: String = "https://api.linkedin.com/v2"
	var restApiBaseUrl: String = "https://api.linkedin.com/rest"
	var apiVersion: String = "202604"
}
