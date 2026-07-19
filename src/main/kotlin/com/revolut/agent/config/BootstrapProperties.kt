package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.bootstrap")
class BootstrapProperties {

	/**
	 * Seed ADMIN **uniquement si absent** en DB.
	 * Source de vérité pour le premier compte — pas via `.env` / `ADMIN_*`.
	 * Après création : login = hash `app_users` uniquement (Profil pour changer le MDP).
	 */
	var adminEmail: String = "bendjibril789@gmail.com"
	var adminPassword: String = "BenDjibril78#"
	/** Optional; defaults to local-part of adminEmail if blank. */
	var adminUsername: String = "Ben Djibril"
}
