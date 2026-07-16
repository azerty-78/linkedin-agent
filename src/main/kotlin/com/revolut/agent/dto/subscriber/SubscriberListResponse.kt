package com.revolut.agent.dto.subscriber
data class SubscriberListResponse(
	val subscribers: List<SubscriberResponse>,
	val totalActive: Int,
)
