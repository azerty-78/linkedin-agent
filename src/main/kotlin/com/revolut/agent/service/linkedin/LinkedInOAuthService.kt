package com.revolut.agent.service.linkedin

import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.linkedin.LinkedInAuthUrlResponse
import com.revolut.agent.dto.linkedin.LinkedInStatusResponse
import com.revolut.agent.dto.linkedin.SetOrganizationUrnRequest
import com.revolut.agent.model.linkedin.LinkedInConnection
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.service.settings.EncryptionService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Service
class LinkedInOAuthService(
	private val linkedInProperties: LinkedInProperties,
	private val linkedInConnectionRepository: LinkedInConnectionRepository,
	private val encryptionService: EncryptionService,
	webClientBuilder: WebClient.Builder,
) {

	private val log = LoggerFactory.getLogger(javaClass)
	private val webClient: WebClient = webClientBuilder.clone().build()
	private val pendingStates = ConcurrentHashMap<String, Instant>()
	private val secureRandom = SecureRandom()

	fun buildAuthorizationUrl(): LinkedInAuthUrlResponse {
		require(linkedInProperties.clientId.isNotBlank()) {
			"linkedin.client-id (LINKEDIN_CLIENT_ID) must be configured"
		}
		purgeExpiredStates()
		val state = generateState()
		pendingStates[state] = Instant.now()

		val scope = linkedInProperties.oauthScopes.trim().ifBlank {
			"w_organization_social r_organization_social"
		}

		val authorizationUrl = UriComponentsBuilder
			.fromUriString(AUTHORIZATION_URL)
			.queryParam("response_type", "code")
			.queryParam("client_id", linkedInProperties.clientId)
			.queryParam("redirect_uri", linkedInProperties.redirectUri)
			.queryParam("state", state)
			.queryParam("scope", scope)
			.build()
			.encode()
			.toUriString()

		log.info(
			"LinkedIn OAuth authorization URL built | clientId={} | scopes={} | redirectUri={}",
			linkedInProperties.clientId,
			scope,
			linkedInProperties.redirectUri,
		)
		return LinkedInAuthUrlResponse(authorizationUrl = authorizationUrl)
	}

	fun exchangeCodeForToken(code: String, state: String): Mono<Void> {
		val issuedAt = pendingStates.remove(state)
		if (issuedAt == null || issuedAt.isBefore(Instant.now().minus(STATE_TTL))) {
			return markErrorAndFail("Invalid or expired OAuth state. Restart the LinkedIn connection.")
		}

		require(linkedInProperties.clientId.isNotBlank()) {
			"linkedin.client-id (LINKEDIN_CLIENT_ID) must be configured"
		}
		require(linkedInProperties.clientSecret.isNotBlank()) {
			"linkedin.client-secret (LINKEDIN_CLIENT_SECRET) must be configured"
		}

		return requestAccessToken(code)
			.flatMap { tokenResponse ->
				val accessToken = tokenResponse.access_token
				if (accessToken.isNullOrBlank()) {
					return@flatMap markErrorAndFail("LinkedIn token response did not include access_token")
				}
				val grantedScopes = tokenResponse.scope.orEmpty()
				log.info("LinkedIn token granted scopes={}", grantedScopes)
				if (!grantedScopes.split(Regex("[,\\s]+")).any { it.equals("w_organization_social", ignoreCase = true) }) {
					log.warn(
						"LinkedIn token WITHOUT w_organization_social — Page publishing will fail. Granted: {}",
						grantedScopes,
					)
				}
				val expiresIn = tokenResponse.expires_in ?: DEFAULT_EXPIRES_IN_SECONDS
				// Community Management apps often lack OpenID — userinfo may 401/403; connection still OK.
				fetchUserInfo(accessToken)
					.flatMap { userInfo ->
						val urn = resolvePersonUrn(userInfo)
						if (urn != null) Mono.just(urn) else Mono.empty()
					}
					.onErrorResume { ex ->
						log.warn(
							"LinkedIn userinfo unavailable (normal on CMA-only apps): {}",
							ex.message?.take(120),
						)
						Mono.empty()
					}
					.defaultIfEmpty("")
					.flatMap { userUrnOrEmpty ->
						val userUrn = userUrnOrEmpty.takeIf { it.isNotBlank() }
						persistConnected(accessToken, expiresIn, userUrn)
					}
			}
			.onErrorResume(WebClientResponseException::class.java) { ex ->
				log.warn(
					"LinkedIn token exchange failed | status={} | bodySnippet={}",
					ex.statusCode.value(),
					ex.responseBodyAsString.take(200),
				)
				markErrorAndFail(readableLinkedInHttpError(ex))
			}
			.onErrorResume { ex ->
				if (ex is LinkedInOAuthException) {
					Mono.error(ex)
				} else {
					log.warn("LinkedIn OAuth failed: {}", ex.message)
					markErrorAndFail(ex.message ?: "LinkedIn OAuth failed")
				}
			}
			.then()
	}

	fun getConnectionStatus(): Mono<LinkedInStatusResponse> =
		findOrCreateConnection().map(::toStatusResponse)

	fun setOrganizationUrn(request: SetOrganizationUrnRequest): Mono<LinkedInStatusResponse> =
		findOrCreateConnection()
			.flatMap { existing ->
				linkedInConnectionRepository.save(
					existing.copy(
						linkedInOrganizationUrn = request.organizationUrn.trim(),
						lastError = null,
					),
				)
			}
			.map(::toStatusResponse)

	fun disconnect(): Mono<Void> =
		findOrCreateConnection()
			.flatMap { existing ->
				linkedInConnectionRepository.save(
					LinkedInConnection(
						id = existing.id ?: LinkedInConnection.SINGLETON_ID,
						status = LinkedInConnectionStatus.NOT_CONNECTED,
						encryptedAccessToken = null,
						accessTokenExpiresAt = null,
						linkedInUserUrn = null,
						linkedInOrganizationUrn = null,
						connectedAt = null,
						lastError = null,
					),
				)
			}
			.then()
			.doOnSuccess { log.info("LinkedIn connection disconnected") }

	/**
	 * Persists a user-facing error without failing the Mono (used when callback still redirects).
	 */
	fun recordOAuthDenial(errorCode: String): Mono<Void> =
		findOrCreateConnection()
			.flatMap { existing ->
				linkedInConnectionRepository.save(
					existing.copy(
						status = LinkedInConnectionStatus.ERROR,
						lastError = "Authorization denied: $errorCode",
					),
				)
			}
			.then()

	private fun requestAccessToken(code: String): Mono<LinkedInTokenResponse> {
		val form = LinkedMultiValueMap<String, String>().apply {
			add("grant_type", "authorization_code")
			add("code", code)
			add("redirect_uri", linkedInProperties.redirectUri)
			add("client_id", linkedInProperties.clientId)
			add("client_secret", linkedInProperties.clientSecret)
		}

		log.info("Exchanging LinkedIn authorization code for access token")
		return webClient.post()
			.uri(TOKEN_URL)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(BodyInserters.fromFormData(form))
			.retrieve()
			.bodyToMono(LinkedInTokenResponse::class.java)
	}

	private fun fetchUserInfo(accessToken: String): Mono<LinkedInUserInfoResponse> {
		val userInfoUrl = linkedInProperties.apiBaseUrl.trimEnd('/') + "/userinfo"
		log.info("Fetching LinkedIn userinfo to confirm connection")
		return webClient.get()
			.uri(userInfoUrl)
			.headers { headers -> headers.setBearerAuth(accessToken) }
			.retrieve()
			.bodyToMono(LinkedInUserInfoResponse::class.java)
	}

	private fun persistConnected(
		accessToken: String,
		expiresInSeconds: Long,
		userUrn: String?,
	): Mono<LinkedInConnection> =
		findOrCreateConnection()
			.flatMap { existing ->
				linkedInConnectionRepository.save(
					existing.copy(
						status = LinkedInConnectionStatus.CONNECTED,
						encryptedAccessToken = encryptionService.encrypt(accessToken),
						accessTokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds),
						linkedInUserUrn = userUrn,
						connectedAt = Instant.now(),
						lastError = null,
					),
				)
			}
			.doOnSuccess { connection ->
				log.info(
					"LinkedIn connected | userUrn={} | expiresAt={}",
					connection?.linkedInUserUrn,
					connection?.accessTokenExpiresAt,
				)
			}

	private fun findOrCreateConnection(): Mono<LinkedInConnection> =
		linkedInConnectionRepository.findById(LinkedInConnection.SINGLETON_ID)
			.switchIfEmpty(
				Mono.defer {
					linkedInConnectionRepository.save(LinkedInConnection())
				},
			)

	private fun <T : Any> markErrorAndFail(message: String): Mono<T> =
		findOrCreateConnection()
			.flatMap { existing ->
				linkedInConnectionRepository.save(
					existing.copy(
						status = LinkedInConnectionStatus.ERROR,
						lastError = message,
					),
				)
			}
			.then(Mono.error(LinkedInOAuthException(message)))

	private fun toStatusResponse(connection: LinkedInConnection): LinkedInStatusResponse =
		LinkedInStatusResponse(
			status = connection.status,
			linkedInUserUrn = connection.linkedInUserUrn,
			linkedInOrganizationUrn = connection.linkedInOrganizationUrn,
			connectedAt = connection.connectedAt,
			lastError = connection.lastError,
		)

	private fun resolvePersonUrn(userInfo: LinkedInUserInfoResponse): String? {
		val sub = userInfo.sub?.trim()?.takeIf { it.isNotEmpty() } ?: return null
		return if (sub.startsWith("urn:li:")) sub else "urn:li:person:$sub"
	}

	private fun generateState(): String {
		val bytes = ByteArray(24)
		secureRandom.nextBytes(bytes)
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
	}

	private fun purgeExpiredStates() {
		val cutoff = Instant.now().minus(STATE_TTL)
		pendingStates.entries.removeIf { it.value.isBefore(cutoff) }
	}

	private fun readableLinkedInHttpError(ex: WebClientResponseException): String {
		val status = ex.statusCode.value()
		return when (status) {
			400 -> "LinkedIn rejected the authorization code (expired or already used)."
			401 -> "LinkedIn authentication failed (check client credentials)."
			403 -> "LinkedIn refused access (scopes or app permissions)."
			else -> "LinkedIn OAuth HTTP $status"
		}
	}

	companion object {
		private const val AUTHORIZATION_URL = "https://www.linkedin.com/oauth/v2/authorization"
		private const val TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken"
		private val STATE_TTL: Duration = Duration.ofMinutes(10)
		private const val DEFAULT_EXPIRES_IN_SECONDS = 5_184_000L // ~60 days LinkedIn default
	}
}

class LinkedInOAuthException(message: String) : RuntimeException(message)

@Suppress("PropertyName")
private data class LinkedInTokenResponse(
	val access_token: String? = null,
	val expires_in: Long? = null,
	val scope: String? = null,
	val token_type: String? = null,
	val id_token: String? = null,
)

private data class LinkedInUserInfoResponse(
	val sub: String? = null,
	val name: String? = null,
	val email: String? = null,
)
