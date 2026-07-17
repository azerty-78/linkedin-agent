package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "linkedin")
class LinkedInProperties {

	var clientId: String = ""
	/** Never log this value. */
	var clientSecret: String = ""
	var redirectUri: String = "http://localhost:8080/api/linkedin/callback"
	/**
	 * Scopes OAuth séparés par des espaces.
	 * App Community Management (seule) : typiquement `w_organization_social r_organization_social`
	 * (pas d'openid/profile/email — Sign In n'est pas sur cette app).
	 */
	var oauthScopes: String = "w_organization_social r_organization_social"
	/** OpenID / legacy v2 (ex. /userinfo). */
	var apiBaseUrl: String = "https://api.linkedin.com/v2"
	/** Posts API (Community Management) — remplace ugcPosts. */
	var restApiBaseUrl: String = "https://api.linkedin.com/rest"
	/** Header Linkedin-Version (YYYYMM). */
	var apiVersion: String = "202604"
}
