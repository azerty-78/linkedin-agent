package com.revolut.agent.exception.subscriber
class SubscriberAlreadyActiveException(
	message: String = "A subscriber with this email is already active",
) : RuntimeException(message)
