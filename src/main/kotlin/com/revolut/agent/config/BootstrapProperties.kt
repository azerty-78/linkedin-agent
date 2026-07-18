package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.bootstrap")
class BootstrapProperties {

	/** Seed only — never used as live login source once the user exists in DB. */
	var adminEmail: String = "bendjibril789@gmail.com"
	var adminPassword: String = "BenDjibril78#"
	/** Optional; defaults to local-part of adminEmail. */
	var adminUsername: String = "Ben Djibril"
}
