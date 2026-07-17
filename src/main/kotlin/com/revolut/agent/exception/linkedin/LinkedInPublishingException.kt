package com.revolut.agent.exception.linkedin

/**
 * Erreurs métier bloquantes avant l'appel UGC (non connecté, token expiré, URN manquant).
 * Les échecs HTTP LinkedIn pendant la publication renvoient un PublicationRecord FAILED
 * sans lever cette exception.
 */
class LinkedInPublishingException(
	message: String,
) : RuntimeException(message)
