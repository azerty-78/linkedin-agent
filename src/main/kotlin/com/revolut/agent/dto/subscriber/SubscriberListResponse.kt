package com.revolut.agent.dto

data class SubscriberListResponse(
	val subscribers: List<SubscriberResponse>,
	val totalActive: Int,
)
