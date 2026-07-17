package com.revolut.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class AppFrontendProperties {

	var frontendUrl: String = "http://localhost:3000"
}
