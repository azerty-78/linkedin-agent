package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Config LinkedIn non secrète — 100 % serveur (pas d’UI settings).
 * Client ID / secret → OrganizationSettings (DB chiffrée).
 *
 * Properties :
 * - `linkedin.redirect-uri`
 * - `linkedin.oauth-scopes`
 */
@ConfigurationProperties(prefix = "linkedin")
class LinkedInProperties {

	/** Callback OAuth backend, ex. https://api…/api/linkedin/callback */
	var redirectUri: String = "http://localhost:8080/api/linkedin/callback"

	/** Scopes OAuth (Community Management / org). */
	var oauthScopes: String = "w_organization_social r_organization_social"

	var apiBaseUrl: String = "https://api.linkedin.com/v2"
	var restApiBaseUrl: String = "https://api.linkedin.com/rest"
	var apiVersion: String = "202604"
}
