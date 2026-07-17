package com.revolut.agent.exception.linkedin

class LinkedInAccountLimitException(
	message: String = "Maximum ${com.revolut.agent.model.linkedin.LinkedInConnection.MAX_ACCOUNTS} comptes LinkedIn atteint.",
) : RuntimeException(message)
