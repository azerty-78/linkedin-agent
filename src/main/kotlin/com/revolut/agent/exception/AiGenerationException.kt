package com.revolut.agent.exception

class AiGenerationException(
	message: String,
	cause: Throwable? = null,
) : RuntimeException(message, cause)
