package com.revolut.agent.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "subscribers")
data class Subscriber(
	@Id
	val id: String? = null,
	@Indexed(unique = true)
	val email: String,
	val fullName: String? = null,
	val status: SubscriberStatus = SubscriberStatus.ACTIVE,
	@Indexed(unique = true)
	val unsubscribeToken: String,
	val subscribedAt: Instant = Instant.now(),
	val unsubscribedAt: Instant? = null,
)
