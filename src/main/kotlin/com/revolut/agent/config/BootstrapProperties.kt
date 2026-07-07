package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.bootstrap")
class BootstrapProperties {

	var adminEmail: String = "admin@localhost"
	var adminPassword: String = "ChangeMe123!"
}
