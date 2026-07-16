package com.revolut.agent.dto.subscriber
import com.revolut.agent.model.subscriber.SubscriberStatus
import java.time.Instant

data class SubscriberResponse(
	val id: String,
	val email: String,
	val fullName: String?,
	val status: SubscriberStatus,
	val subscribedAt: Instant,
	val unsubscribedAt: Instant?,
)
