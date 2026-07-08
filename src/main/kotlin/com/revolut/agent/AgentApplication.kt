package com.revolut.agent

import com.revolut.agent.config.AiModelProperties
import com.revolut.agent.config.BootstrapProperties
import com.revolut.agent.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class, BootstrapProperties::class, AiModelProperties::class)
class AgentApplication

fun main(args: Array<String>) {
	runApplication<AgentApplication>(*args)
}
