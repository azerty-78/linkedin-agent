package com.revolut.agent.exception.linkedin

class LinkedInAppLimitException(
	message: String = "Maximum ${com.revolut.agent.model.linkedin.LinkedInApp.MAX_APPS} applications LinkedIn atteint.",
) : RuntimeException(message)

class LinkedInAppConflictException(
	message: String,
) : RuntimeException(message)
