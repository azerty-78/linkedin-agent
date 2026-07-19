package com.revolut.agent

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Smoke context — nécessite MongoDB (service CI ou local via [MONGODB_URI]).
 */
@SpringBootTest
class AgentApplicationTests {

	companion object {
		@JvmStatic
		@DynamicPropertySource
		fun mongoProperties(registry: DynamicPropertyRegistry) {
			val uri = System.getenv("MONGODB_URI")
				?: "mongodb://localhost:27017/linkedin_agent_db"
			registry.add("spring.mongodb.uri") { uri }
		}
	}

	@Test
	fun contextLoads() {
	}
}
