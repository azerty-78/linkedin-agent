package com.revolut.agent.exception.publication

class PublicationAlreadyRunningException(
	message: String = "Une publication est déjà en cours (IN_PROGRESS). Réessayez plus tard.",
) : RuntimeException(message)
