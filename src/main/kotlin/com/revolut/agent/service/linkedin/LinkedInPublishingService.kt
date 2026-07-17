package com.revolut.agent.service.linkedin

import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.exception.linkedin.LinkedInPublishingException
import com.revolut.agent.model.linkedin.LinkedInConnection
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.model.linkedin.PublicationRecord
import com.revolut.agent.model.linkedin.PublicationStatus
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.linkedin.PublicationRecordRepository
import com.revolut.agent.service.settings.EncryptionService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class LinkedInPublishingService(
	private val linkedInProperties: LinkedInProperties,
	private val linkedInConnectionRepository: LinkedInConnectionRepository,
	private val publicationRecordRepository: PublicationRecordRepository,
	private val encryptionService: EncryptionService,
	webClientBuilder: WebClient.Builder,
) {

	private val log = LoggerFactory.getLogger(javaClass)
	private val webClient: WebClient = webClientBuilder.clone().build()

	/**
	 * Publie [content] sur le profil membre connecté (UGC Posts).
	 * En cas d'échec HTTP LinkedIn : retourne un [PublicationRecord] FAILED (pas d'exception 500).
	 * Erreurs métier préalables (non connecté / token expiré) → [LinkedInPublishingException] (422).
	 */
	fun publish(content: String, aiProvider: LlmProvider): Mono<PublicationRecord> {
		val trimmed = content.trim()
		require(trimmed.isNotEmpty()) { "Publication content must not be blank" }

		return loadConnectedConnection()
			.flatMap { connection ->
				val accessToken = decryptAccessToken(connection)
				val targetUrn = resolveTargetUrn(connection)

				val pending = PublicationRecord(
					content = trimmed,
					aiProvider = aiProvider,
					targetUrn = targetUrn,
					status = PublicationStatus.PENDING,
					createdAt = Instant.now(),
				)

				publicationRecordRepository.save(pending)
					.flatMap { saved ->
						callUgcPostsApi(accessToken, targetUrn, trimmed)
							.flatMap { postId ->
								publicationRecordRepository.save(
									saved.copy(
										status = PublicationStatus.PUBLISHED,
										linkedInPostId = postId,
										publishedAt = Instant.now(),
										errorMessage = null,
									),
								)
							}
							.onErrorResume { error ->
								val message = readablePublishError(error)
								log.warn(
									"LinkedIn UGC publish failed | recordId={} | httpOrCause={}",
									saved.id,
									summarizeErrorForLog(error),
								)
								publicationRecordRepository.save(
									saved.copy(
										status = PublicationStatus.FAILED,
										errorMessage = message,
									),
								)
							}
					}
			}
	}

	private fun loadConnectedConnection(): Mono<LinkedInConnection> =
		linkedInConnectionRepository.findById(LinkedInConnection.SINGLETON_ID)
			.switchIfEmpty(
				Mono.error(
					LinkedInPublishingException(
						"LinkedIn non connecté, allez sur /api/linkedin/auth-url",
					),
				),
			)
			.flatMap { connection ->
				when {
					connection.status != LinkedInConnectionStatus.CONNECTED ->
						Mono.error(
							LinkedInPublishingException(
								"LinkedIn non connecté, allez sur /api/linkedin/auth-url",
							),
						)

					isTokenExpired(connection) ->
						markExpired(connection)
							.then(
								Mono.error(
									LinkedInPublishingException(
										"Token LinkedIn expiré — reconnectez-vous via /api/linkedin/auth-url",
									),
								),
							)

					else -> Mono.just(connection)
				}
			}

	private fun isTokenExpired(connection: LinkedInConnection): Boolean {
		val expiresAt = connection.accessTokenExpiresAt ?: return true
		return !expiresAt.isAfter(Instant.now())
	}

	private fun markExpired(connection: LinkedInConnection): Mono<LinkedInConnection> =
		linkedInConnectionRepository.save(
			connection.copy(
				status = LinkedInConnectionStatus.EXPIRED,
				lastError = "Access token expired",
			),
		)

	private fun decryptAccessToken(connection: LinkedInConnection): String {
		val encrypted = connection.encryptedAccessToken
			?: throw LinkedInPublishingException(
				"LinkedIn non connecté, allez sur /api/linkedin/auth-url",
			)
		return encryptionService.tryDecrypt(encrypted)
			?: throw LinkedInPublishingException(
				"Impossible de déchiffrer le token LinkedIn — reconnectez-vous via /api/linkedin/auth-url",
			)
	}

	/**
	 * Cible actuelle : profil personnel ([LinkedInConnection.linkedInUserUrn]).
	 * Quand w_organization_social sera approuvé, brancher ici
	 * [LinkedInConnection.linkedInOrganizationUrn] (champ déjà stocké via PUT /organization-urn).
	 */
	private fun resolveTargetUrn(connection: LinkedInConnection): String {
		val userUrn = connection.linkedInUserUrn?.trim()?.takeIf { it.isNotEmpty() }
			?: throw LinkedInPublishingException(
				"URN membre LinkedIn manquant — reconnectez-vous via /api/linkedin/auth-url",
			)
		// Future: if (connection.linkedInOrganizationUrn != null) return organization URN
		return userUrn
	}

	/**
	 * POST /v2/ugcPosts.
	 * L'id du post est lu depuis le header de réponse `x-restli-id` (pas le body — body souvent vide en 201).
	 * Fallback : header `Location` si présent.
	 */
	private fun callUgcPostsApi(
		accessToken: String,
		authorUrn: String,
		text: String,
	): Mono<String> {
		val ugcUrl = linkedInProperties.apiBaseUrl.trimEnd('/') + "/ugcPosts"
		val body = buildUgcPostBody(authorUrn, text)

		log.info("LinkedIn UGC publish started | authorUrn={} | contentLength={}", authorUrn, text.length)

		return webClient.post()
			.uri(ugcUrl)
			.contentType(MediaType.APPLICATION_JSON)
			.header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
			.header(RESTLI_PROTOCOL_HEADER, RESTLI_PROTOCOL_VERSION)
			.bodyValue(body)
			.exchangeToMono { response ->
				val status = response.statusCode()
				val headers = response.headers().asHttpHeaders()
				val postId = extractPostId(headers)

				if (status.is2xxSuccessful) {
					response.releaseBody().then(
						if (!postId.isNullOrBlank()) {
							log.info("LinkedIn UGC publish succeeded | postId={}", postId)
							Mono.just(postId)
						} else {
							log.warn(
								"LinkedIn UGC 2xx but missing x-restli-id / Location | httpStatus={}",
								status.value(),
							)
							Mono.just("unknown")
						},
					)
				} else {
					response.createException().flatMap { Mono.error(it) }
				}
			}
	}

	private fun buildUgcPostBody(authorUrn: String, text: String): Map<String, Any> =
		mapOf(
			"author" to authorUrn,
			"lifecycleState" to "PUBLISHED",
			"specificContent" to mapOf(
				"com.linkedin.ugc.ShareContent" to mapOf(
					"shareCommentary" to mapOf("text" to text),
					"shareMediaCategory" to "NONE",
				),
			),
			"visibility" to mapOf(
				"com.linkedin.ugc.MemberNetworkVisibility" to "PUBLIC",
			),
		)

	private fun extractPostId(headers: HttpHeaders): String? {
		val restLiId = headers.getFirst(RESTLI_ID_HEADER)
			?: headers.getFirst(RESTLI_ID_HEADER.lowercase())
		if (!restLiId.isNullOrBlank()) {
			return restLiId.trim()
		}
		val location = headers.getFirst(HttpHeaders.LOCATION)?.trim()
		if (!location.isNullOrBlank()) {
			return location.substringAfterLast('/').takeIf { it.isNotBlank() } ?: location
		}
		return null
	}

	private fun readablePublishError(error: Throwable): String {
		val http = error as? WebClientResponseException
			?: (error.cause as? WebClientResponseException)
		if (http != null) {
			return when (http.statusCode.value()) {
				401 -> "Token LinkedIn invalide ou révoqué — reconnectez-vous."
				403 -> "Permission refusée (scope w_member_social manquant ou app non autorisée)."
				422 -> "LinkedIn a rejeté le contenu du post (format ou politique)."
				429 -> "Quota LinkedIn dépassé (rate limit) — réessayez plus tard."
				in 500..599 -> "Erreur serveur LinkedIn (${http.statusCode.value()}) — réessayez plus tard."
				else -> "Publication LinkedIn échouée (HTTP ${http.statusCode.value()})."
			}
		}
		return error.message?.take(200)?.ifBlank { null } ?: "Publication LinkedIn échouée"
	}

	/** Jamais de token / Authorization dans les logs. */
	private fun summarizeErrorForLog(error: Throwable): String {
		val http = error as? WebClientResponseException
			?: (error.cause as? WebClientResponseException)
		return if (http != null) {
			"HTTP ${http.statusCode.value()}"
		} else {
			error.javaClass.simpleName + ": " + (error.message?.take(80) ?: "")
		}
	}

	companion object {
		private const val RESTLI_PROTOCOL_HEADER = "X-Restli-Protocol-Version"
		private const val RESTLI_PROTOCOL_VERSION = "2.0.0"
		private const val RESTLI_ID_HEADER = "x-restli-id"
	}
}
