package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Valeurs non secrètes LinkedIn uniquement.
 * Client ID / secret → OrganizationSettings (DB chiffrée), plus dans .env.
 */
@ConfigurationProperties(prefix = "linkedin")
class LinkedInProperties {

	var defaultRedirectUri: String = "http://localhost:8080/api/linkedin/callback"
	var defaultOauthScopes: String = "w_organization_social r_organization_social"
	var apiBaseUrl: String = "https://api.linkedin.com/v2"
	var restApiBaseUrl: String = "https://api.linkedin.com/rest"
	var apiVersion: String = "202604"
}
