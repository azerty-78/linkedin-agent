package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.bootstrap")
class BootstrapProperties {

	var adminEmail: String = "bendjibril789@gmail.com"
	var adminPassword: String = "BenDjibril78#"
}
