package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "linkedin")
class LinkedInProperties {

	var clientId: String = ""
	/** Never log this value. */
	var clientSecret: String = ""
	var redirectUri: String = "http://localhost:8080/api/linkedin/callback"
	var apiBaseUrl: String = "https://api.linkedin.com/v2"
}
