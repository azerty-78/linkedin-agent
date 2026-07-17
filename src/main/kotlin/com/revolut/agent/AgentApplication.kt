package com.revolut.agent

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.config.AppFrontendProperties
import com.revolut.agent.config.BootstrapProperties
import com.revolut.agent.config.JwtProperties
import com.revolut.agent.config.LinkedInProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(
	JwtProperties::class,
	BootstrapProperties::class,
	AiModelProperties::class,
	LinkedInProperties::class,
	AppFrontendProperties::class,
)
class AgentApplication

fun main(args: Array<String>) {
	runApplication<AgentApplication>(*args)
}
