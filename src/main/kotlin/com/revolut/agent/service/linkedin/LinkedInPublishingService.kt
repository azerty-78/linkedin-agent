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
	 * Publie [content] sur la **Page entreprise** du compte LinkedIn **actif**.
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
	 * Cible : Page entreprise uniquement ([LinkedInConnection.linkedInOrganizationUrn]).
	 * Le profil perso ([LinkedInConnection.linkedInUserUrn]) sert à l'OAuth / identité admin,
	 * pas comme auteur du post. Pas de repost vers le feed personnel.
	 */
	private fun resolveTargetUrn(connection: LinkedInConnection): String {
		val orgUrn = connection.linkedInOrganizationUrn?.trim()?.takeIf { it.isNotEmpty() }
			?: throw LinkedInPublishingException(
				"URN de la Page LinkedIn manquant. " +
					"Renseignez-le via PUT /api/linkedin/organization-urn " +
					"(ex. urn:li:organization:123456789).",
			)
		require(orgUrn.startsWith("urn:li:organization:")) {
			"linkedInOrganizationUrn must start with urn:li:organization:"
		}
		return orgUrn
	}

	/**
	 * POST https://api.linkedin.com/rest/posts (Posts API — remplace l'ancien /v2/ugcPosts).
	 * Id du post : header `x-restli-id` (body souvent vide en 201).
	 */
	private fun callUgcPostsApi(
		accessToken: String,
		authorUrn: String,
		text: String,
	): Mono<String> {
		val postsUrl = linkedInProperties.restApiBaseUrl.trimEnd('/') + "/posts"
		val body = buildPostsApiBody(authorUrn, text)

		log.info(
			"LinkedIn Posts API publish started | authorUrn={} | contentLength={} | apiVersion={}",
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
				403 -> "Permission refusée (403). Pour publier sur une Page : " +
					"1) scope w_organization_social (Community Management / Share), " +
					"2) reconnecter OAuth, " +
					"3) compte admin de la Page, " +
					"4) URN Page renseigné (PUT /organization-urn)."
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
