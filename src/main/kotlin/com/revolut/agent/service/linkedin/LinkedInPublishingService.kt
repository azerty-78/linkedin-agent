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
	private val linkedInOAuthService: LinkedInOAuthService,
	private val linkedInConnectionRepository: LinkedInConnectionRepository,
	private val publicationRecordRepository: PublicationRecordRepository,
	private val encryptionService: EncryptionService,
	webClientBuilder: WebClient.Builder,
) {

	private val log = LoggerFactory.getLogger(javaClass)
	private val webClient: WebClient = webClientBuilder.clone().build()

	/**
	 * Publie [content] sur le **profil perso** du compte LinkedIn **actif**
	 * (author = `linkedInUserUrn`). Page org seulement si person absent et URN org fourni.
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
					accountId = connection.id,
					accountLabel = connection.label,
					createdAt = Instant.now(),
				)

				publicationRecordRepository.save(pending)
					.flatMap { saved ->
						publishToLinkedIn(accessToken, targetUrn, trimmed)
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
									"LinkedIn publish failed | recordId={} | httpOrCause={}",
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
		linkedInOAuthService.resolveActiveAccount()
			.switchIfEmpty(
				Mono.error(
					LinkedInPublishingException(
						"Aucun compte LinkedIn actif. Connectez un compte via /api/linkedin/accounts/connect " +
							"et sélectionnez-le dans PUT /api/settings (activeLinkedInAccountId).",
					),
				),
			)
			.flatMap { connection ->
				when {
					connection.status != LinkedInConnectionStatus.CONNECTED ->
						Mono.error(
							LinkedInPublishingException(
								"Compte LinkedIn non connecté (id=${connection.id}). Relancez OAuth.",
							),
						)

					isTokenExpired(connection) ->
						markExpired(connection)
							.then(
								Mono.error(
									LinkedInPublishingException(
										"Token LinkedIn expiré — reconnectez le compte via " +
											"/api/linkedin/accounts/${connection.id}/auth-url",
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
	 * Auteur par défaut = profil perso (`urn:li:person:…`).
	 * Page org uniquement si person URN absent et org URN fourni (mode futur).
	 */
	private fun resolveTargetUrn(connection: LinkedInConnection): String {
		val personUrn = connection.linkedInUserUrn?.trim()?.takeIf { it.isNotEmpty() }
		if (personUrn != null) {
			require(personUrn.startsWith("urn:li:person:")) {
				"linkedInUserUrn must start with urn:li:person:"
			}
			return personUrn
		}
		val orgUrn = connection.linkedInOrganizationUrn?.trim()?.takeIf { it.isNotEmpty() }
			?: throw LinkedInPublishingException(
				"Profil LinkedIn (person URN) manquant. Reconnectez OAuth via " +
					"POST /api/linkedin/accounts/connect (scopes openid + w_member_social).",
			)
		require(orgUrn.startsWith("urn:li:organization:")) {
			"linkedInOrganizationUrn must start with urn:li:organization:"
		}
		return orgUrn
	}

	private fun publishToLinkedIn(
		accessToken: String,
		authorUrn: String,
		text: String,
	): Mono<String> =
		if (authorUrn.startsWith("urn:li:person:")) {
			callMemberUgcPostsApi(accessToken, authorUrn, text)
		} else {
			callOrganizationPostsApi(accessToken, authorUrn, text)
		}

	/**
	 * Share on LinkedIn — profil membre (`w_member_social`).
	 * POST https://api.linkedin.com/v2/ugcPosts
	 */
	private fun callMemberUgcPostsApi(
		accessToken: String,
		authorUrn: String,
		text: String,
	): Mono<String> {
		val ugcUrl = linkedInProperties.apiBaseUrl.trimEnd('/') + "/ugcPosts"
		val body = mapOf(
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

		log.info(
			"LinkedIn UGC (member) publish started | authorUrn={} | contentLength={}",
			authorUrn,
			text.length,
		)

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
							response.bodyToMono(String::class.java).defaultIfEmpty("").map { raw ->
								val fromBody = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
								(fromBody ?: "unknown").also {
									log.info("LinkedIn UGC publish succeeded | postId={}", it)
								}
							}
						},
					)
				} else {
					response.createException().flatMap { Mono.error(it) }
				}
			}
	}

	/**
	 * Posts API org (Community Management) — réservé mode Page.
	 * POST https://api.linkedin.com/rest/posts
	 */
	private fun callOrganizationPostsApi(
		accessToken: String,
		authorUrn: String,
		text: String,
	): Mono<String> {
		val postsUrl = linkedInProperties.restApiBaseUrl.trimEnd('/') + "/posts"
		val body = buildPostsApiBody(authorUrn, text)

		log.info(
			"LinkedIn Posts API (org) publish started | authorUrn={} | contentLength={} | apiVersion={}",
			authorUrn,
			text.length,
			linkedInProperties.apiVersion,
		)

		return webClient.post()
			.uri(postsUrl)
			.contentType(MediaType.APPLICATION_JSON)
			.header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
			.header(RESTLI_PROTOCOL_HEADER, RESTLI_PROTOCOL_VERSION)
			.header(LINKEDIN_VERSION_HEADER, linkedInProperties.apiVersion)
			.bodyValue(body)
			.exchangeToMono { response ->
				val status = response.statusCode()
				val headers = response.headers().asHttpHeaders()
				val postId = extractPostId(headers)

				if (status.is2xxSuccessful) {
					response.releaseBody().then(
						if (!postId.isNullOrBlank()) {
							log.info("LinkedIn Posts API publish succeeded | postId={}", postId)
							Mono.just(postId)
						} else {
							log.warn(
								"LinkedIn Posts API 2xx but missing x-restli-id / Location | httpStatus={}",
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

	/** Payload Posts API (Community Management), pas le schéma legacy ugcPosts. */
	private fun buildPostsApiBody(authorUrn: String, text: String): Map<String, Any> =
		mapOf(
			"author" to authorUrn,
			"commentary" to text,
			"visibility" to "PUBLIC",
			"distribution" to mapOf(
				"feedDistribution" to "MAIN_FEED",
				"targetEntities" to emptyList<Any>(),
				"thirdPartyDistributionChannels" to emptyList<Any>(),
			),
			"lifecycleState" to "PUBLISHED",
			"isReshareDisabledByAuthor" to false,
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
			val linkedInDetail = sanitizeLinkedInErrorBody(http.responseBodyAsString)
			val base = when (http.statusCode.value()) {
				401 -> "Token LinkedIn invalide ou révoqué — reconnectez-vous via /api/linkedin/auth-url."
				403 -> "Permission refusée (403). Pour publier sur votre profil : " +
					"1) scopes openid profile email w_member_social, " +
					"2) reconnecter OAuth (Login LinkedIn), " +
					"3) person URN présent (health AUTHOR_URN_SET)."
				422 -> "LinkedIn a rejeté le contenu du post (format ou politique)."
				429 -> "Quota LinkedIn dépassé (rate limit) — réessayez plus tard."
				in 500..599 -> "Erreur serveur LinkedIn (${http.statusCode.value()}) — réessayez plus tard."
				else -> "Publication LinkedIn échouée (HTTP ${http.statusCode.value()})."
			}
			return if (linkedInDetail != null) "$base Détail LinkedIn: $linkedInDetail" else base
		}
		return error.message?.take(200)?.ifBlank { null } ?: "Publication LinkedIn échouée"
	}

	/** Extrait un message utile du body LinkedIn sans headers/tokens. */
	private fun sanitizeLinkedInErrorBody(raw: String?): String? {
		if (raw.isNullOrBlank()) return null
		val trimmed = raw.replace(Regex("\\s+"), " ").take(280)
		// Évite de remonter quelque chose qui ressemblerait à un bearer
		if (trimmed.contains("Bearer", ignoreCase = true) || trimmed.contains("access_token", ignoreCase = true)) {
			return trimmed.replace(Regex("(?i)bearer\\s+\\S+"), "[redacted]")
				.replace(Regex("(?i)\"access_token\"\\s*:\\s*\"[^\"]*\""), "\"access_token\":\"[redacted]\"")
				.take(280)
		}
		return trimmed
	}

	/** Jamais de token / Authorization dans les logs. */
	private fun summarizeErrorForLog(error: Throwable): String {
		val http = error as? WebClientResponseException
			?: (error.cause as? WebClientResponseException)
		return if (http != null) {
			val detail = sanitizeLinkedInErrorBody(http.responseBodyAsString)?.take(120) ?: ""
			"HTTP ${http.statusCode.value()}" + if (detail.isNotEmpty()) " | $detail" else ""
		} else {
			error.javaClass.simpleName + ": " + (error.message?.take(80) ?: "")
		}
	}

	companion object {
		private const val RESTLI_PROTOCOL_HEADER = "X-Restli-Protocol-Version"
		private const val RESTLI_PROTOCOL_VERSION = "2.0.0"
		private const val LINKEDIN_VERSION_HEADER = "Linkedin-Version"
		private const val RESTLI_ID_HEADER = "x-restli-id"
	}
}
