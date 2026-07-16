package com.revolut.agent.exception.ai
class AiGenerationException(
	message: String,
	cause: Throwable? = null,
) : RuntimeException(message, cause)
