package com.revolut.agent.service.linkedin

import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.linkedin.LinkedInAccountListResponse
import com.revolut.agent.dto.linkedin.LinkedInAuthUrlResponse
import com.revolut.agent.dto.linkedin.LinkedInStatusResponse
import com.revolut.agent.dto.linkedin.SetOrganizationUrnRequest
import com.revolut.agent.dto.linkedin.UpdateLinkedInAccountRequest
import com.revolut.agent.exception.linkedin.LinkedInAccountLimitException
import com.revolut.agent.model.linkedin.LinkedInConnection
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.service.settings.EncryptionService
import com.revolut.agent.service.settings.LinkedInAppCredentials
import com.revolut.agent.service.settings.OrganizationSettingsService
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
	private val organizationSettingsService: OrganizationSettingsService,
	private val encryptionService: EncryptionService,
	webClientBuilder: WebClient.Builder,
) {

	private val log = LoggerFactory.getLogger(javaClass)
	private val webClient: WebClient = webClientBuilder.clone().build()
	private val pendingStates = ConcurrentHashMap<String, PendingOAuth>()
	private val secureRandom = SecureRandom()

	/** Démarre OAuth pour un **nouveau** compte (max [LinkedInConnection.MAX_ACCOUNTS]). */
	fun buildAuthorizationUrlForNewAccount(label: String? = null): Mono<LinkedInAuthUrlResponse> =
		linkedInConnectionRepository.count()
			.flatMap { count ->
				if (count >= LinkedInConnection.MAX_ACCOUNTS) {
					Mono.error(LinkedInAccountLimitException())
				} else {
					organizationSettingsService.getLinkedInAppCredentials()
						.map { creds ->
							buildAuthUrl(creds, accountId = null, pendingLabel = label?.trim().orEmpty())
						}
				}
			}

	/** Reconnexion OAuth d'un compte existant. */
	fun buildAuthorizationUrlForAccount(accountId: String): Mono<LinkedInAuthUrlResponse> =
		linkedInConnectionRepository.findById(accountId)
			.switchIfEmpty(Mono.error(NoSuchElementException("LinkedIn account not found: $accountId")))
			.flatMap { account ->
				organizationSettingsService.getLinkedInAppCredentials()
					.map { creds ->
						buildAuthUrl(creds, accountId = account.id, pendingLabel = account.label)
					}
			}

	/** @deprecated Compat — équivalent à [buildAuthorizationUrlForNewAccount]. */
	fun buildAuthorizationUrl(): Mono<LinkedInAuthUrlResponse> =
		buildAuthorizationUrlForNewAccount(null)

	fun exchangeCodeForToken(code: String, state: String): Mono<Void> {
		val pending = pendingStates.remove(state)
		if (pending == null || pending.issuedAt.isBefore(Instant.now().minus(STATE_TTL))) {
			return Mono.error(LinkedInOAuthException("Invalid or expired OAuth state. Restart the LinkedIn connection."))
		}

		return organizationSettingsService.getLinkedInAppCredentials()
			.flatMap { creds ->
				requestAccessToken(code, creds)
					.flatMap { tokenResponse ->
						val accessToken = tokenResponse.access_token
						if (accessToken.isNullOrBlank()) {
							return@flatMap Mono.error(LinkedInOAuthException("LinkedIn token response did not include access_token"))
						}
						val grantedScopes = tokenResponse.scope.orEmpty()
						log.info("LinkedIn token granted scopes={}", grantedScopes)
						val expiresIn = tokenResponse.expires_in ?: DEFAULT_EXPIRES_IN_SECONDS
						fetchUserInfo(accessToken)
							.flatMap { userInfo ->
								val urn = resolvePersonUrn(userInfo)
								if (urn != null) Mono.just(urn) else Mono.empty()
							}
							.onErrorResume { ex ->
								log.warn("LinkedIn userinfo unavailable: {}", ex.message?.take(120))
								Mono.empty()
							}
							.defaultIfEmpty("")
							.flatMap { userUrnOrEmpty ->
								persistConnected(
									accountId = pending.accountId,
									label = pending.label,
									accessToken = accessToken,
									expiresInSeconds = expiresIn,
									userUrn = userUrnOrEmpty.takeIf { it.isNotBlank() },
								)
							}
					}
			}
			.onErrorResume(WebClientResponseException::class.java) { ex ->
				log.warn(
					"LinkedIn token exchange failed | status={} | bodySnippet={}",
					ex.statusCode.value(),
					ex.responseBodyAsString.take(200),
				)
				Mono.error(LinkedInOAuthException(readableLinkedInHttpError(ex)))
			}
			.then()
	}

	fun listAccounts(): Mono<LinkedInAccountListResponse> =
		organizationSettingsService.getActiveLinkedInAccountId()
			.flatMap { activeId ->
				linkedInConnectionRepository.findAllByOrderByCreatedAtAsc()
					.map { toStatusResponse(it, activeId) }
					.collectList()
					.map { accounts ->
						LinkedInAccountListResponse(
							accounts = accounts,
							maxAccounts = LinkedInConnection.MAX_ACCOUNTS,
							activeLinkedInAccountId = activeId.takeIf { it.isNotBlank() },
						)
					}
			}

	fun getAccount(accountId: String): Mono<LinkedInStatusResponse> =
		organizationSettingsService.getActiveLinkedInAccountId()
			.flatMap { activeId ->
				linkedInConnectionRepository.findById(accountId)
					.switchIfEmpty(Mono.error(NoSuchElementException("LinkedIn account not found: $accountId")))
					.map { toStatusResponse(it, activeId) }
			}

	/** Status du compte actif (ou premier connecté). */
	fun getConnectionStatus(): Mono<LinkedInStatusResponse> =
		resolveActiveAccount()
			.flatMap { account ->
				organizationSettingsService.getActiveLinkedInAccountId()
					.map { activeId -> toStatusResponse(account, activeId) }
			}
			.switchIfEmpty(
				Mono.just(
					LinkedInStatusResponse(
						id = null,
						label = "",
						status = LinkedInConnectionStatus.NOT_CONNECTED,
						linkedInUserUrn = null,
						linkedInOrganizationUrn = null,
						connectedAt = null,
						lastError = null,
						isActive = false,
					),
				),
			)

	fun setOrganizationUrn(accountId: String, request: SetOrganizationUrnRequest): Mono<LinkedInStatusResponse> =
		linkedInConnectionRepository.findById(accountId)
			.switchIfEmpty(Mono.error(NoSuchElementException("LinkedIn account not found: $accountId")))
			.flatMap { existing ->
				linkedInConnectionRepository.save(
					existing.copy(
						linkedInOrganizationUrn = request.organizationUrn.trim(),
						lastError = null,
						updatedAt = Instant.now(),
					),
				)
			}
			.flatMap { saved ->
				organizationSettingsService.getActiveLinkedInAccountId()
					.map { activeId -> toStatusResponse(saved, activeId) }
			}

	fun updateAccount(accountId: String, request: UpdateLinkedInAccountRequest): Mono<LinkedInStatusResponse> =
		linkedInConnectionRepository.findById(accountId)
			.switchIfEmpty(Mono.error(NoSuchElementException("LinkedIn account not found: $accountId")))
			.flatMap { existing ->
				linkedInConnectionRepository.save(
					existing.copy(
						label = request.label?.trim()?.takeIf { it.isNotEmpty() } ?: existing.label,
						updatedAt = Instant.now(),
					),
				)
			}
			.flatMap { saved ->
				organizationSettingsService.getActiveLinkedInAccountId()
					.map { activeId -> toStatusResponse(saved, activeId) }
			}

	fun deleteAccount(accountId: String): Mono<Void> =
		linkedInConnectionRepository.findById(accountId)
			.switchIfEmpty(Mono.error(NoSuchElementException("LinkedIn account not found: $accountId")))
			.flatMap { account ->
				linkedInConnectionRepository.delete(account)
					.then(
						organizationSettingsService.getActiveLinkedInAccountId()
							.flatMap { activeId ->
								if (activeId == accountId) {
									organizationSettingsService.setActiveLinkedInAccountId(null).then()
								} else {
									Mono.empty()
								}
							},
					)
			}
			.doOnSuccess { log.info("LinkedIn account deleted | id={}", accountId) }

	fun disconnect(): Mono<Void> =
		resolveActiveAccount()
			.flatMap { account -> deleteAccount(requireNotNull(account.id)) }
			.switchIfEmpty(Mono.empty())

	fun recordOAuthDenial(errorCode: String): Mono<Void> {
		log.info("LinkedIn OAuth denied | error={}", errorCode)
		return Mono.empty()
	}

	/** Compte utilisé pour publier. */
	fun resolveActiveAccount(): Mono<LinkedInConnection> =
		organizationSettingsService.getActiveLinkedInAccountId()
			.flatMap { activeId ->
				if (activeId.isNotBlank()) {
					linkedInConnectionRepository.findById(activeId)
						.switchIfEmpty(pickFirstConnected())
				} else {
					pickFirstConnected()
				}
			}

	private fun pickFirstConnected(): Mono<LinkedInConnection> =
		linkedInConnectionRepository.findAllByOrderByCreatedAtAsc()
			.filter { it.status == LinkedInConnectionStatus.CONNECTED }
			.next()

	private fun buildAuthUrl(
		creds: LinkedInAppCredentials,
		accountId: String?,
		pendingLabel: String,
	): LinkedInAuthUrlResponse {
		purgeExpiredStates()
		val state = generateState()
		pendingStates[state] = PendingOAuth(
			issuedAt = Instant.now(),
			accountId = accountId,
			label = pendingLabel,
		)
		val authorizationUrl = UriComponentsBuilder
			.fromUriString(AUTHORIZATION_URL)
			.queryParam("response_type", "code")
			.queryParam("client_id", creds.clientId)
			.queryParam("redirect_uri", creds.redirectUri)
			.queryParam("state", state)
			.queryParam("scope", creds.oauthScopes)
			.build()
			.encode()
			.toUriString()

		log.info(
			"LinkedIn OAuth URL built | clientId={} | scopes={} | accountId={} | redirectUri={}",
			creds.clientId,
			creds.oauthScopes,
			accountId ?: "new",
			creds.redirectUri,
		)
		return LinkedInAuthUrlResponse(authorizationUrl = authorizationUrl, accountId = accountId)
	}

	private fun requestAccessToken(code: String, creds: LinkedInAppCredentials): Mono<LinkedInTokenResponse> {
		val form = LinkedMultiValueMap<String, String>().apply {
			add("grant_type", "authorization_code")
			add("code", code)
			add("redirect_uri", creds.redirectUri)
			add("client_id", creds.clientId)
			add("client_secret", creds.clientSecret)
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
		return webClient.get()
			.uri(userInfoUrl)
			.headers { headers -> headers.setBearerAuth(accessToken) }
			.retrieve()
			.bodyToMono(LinkedInUserInfoResponse::class.java)
	}

	private fun persistConnected(
		accountId: String?,
		label: String,
		accessToken: String,
		expiresInSeconds: Long,
		userUrn: String?,
	): Mono<LinkedInConnection> {
		val now = Instant.now()
		val saveMono = if (accountId != null) {
			linkedInConnectionRepository.findById(accountId)
				.switchIfEmpty(Mono.error(LinkedInOAuthException("Account $accountId not found during OAuth callback")))
				.flatMap { existing ->
					linkedInConnectionRepository.save(
						existing.copy(
							status = LinkedInConnectionStatus.CONNECTED,
							encryptedAccessToken = encryptionService.encrypt(accessToken),
							accessTokenExpiresAt = now.plusSeconds(expiresInSeconds),
							linkedInUserUrn = userUrn ?: existing.linkedInUserUrn,
							connectedAt = now,
							lastError = null,
							updatedAt = now,
						),
					)
				}
		} else {
			linkedInConnectionRepository.count()
				.flatMap { count ->
					if (count >= LinkedInConnection.MAX_ACCOUNTS) {
						Mono.error(LinkedInAccountLimitException())
					} else {
						val defaultLabel = label.ifBlank { "Compte LinkedIn ${count + 1}" }
						linkedInConnectionRepository.save(
							LinkedInConnection(
								label = defaultLabel,
								status = LinkedInConnectionStatus.CONNECTED,
								encryptedAccessToken = encryptionService.encrypt(accessToken),
								accessTokenExpiresAt = now.plusSeconds(expiresInSeconds),
								linkedInUserUrn = userUrn,
								connectedAt = now,
								createdAt = now,
								updatedAt = now,
							),
						)
					}
				}
		}

		return saveMono.flatMap { saved ->
			organizationSettingsService.getActiveLinkedInAccountId()
				.flatMap { activeId ->
					if (activeId.isBlank()) {
						organizationSettingsService.setActiveLinkedInAccountId(saved.id).thenReturn(saved)
					} else {
						Mono.just(saved)
					}
				}
				.doOnSuccess {
					log.info("LinkedIn connected | accountId={} | userUrn={}", saved.id, saved.linkedInUserUrn)
				}
		}
	}

	private fun toStatusResponse(connection: LinkedInConnection, activeId: String): LinkedInStatusResponse =
		LinkedInStatusResponse(
			id = connection.id,
			label = connection.label,
			status = connection.status,
			linkedInUserUrn = connection.linkedInUserUrn,
			linkedInOrganizationUrn = connection.linkedInOrganizationUrn,
			connectedAt = connection.connectedAt,
			lastError = connection.lastError,
			isActive = connection.id != null && activeId.isNotBlank() && connection.id == activeId,
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
		pendingStates.entries.removeIf { it.value.issuedAt.isBefore(cutoff) }
	}

	private fun readableLinkedInHttpError(ex: WebClientResponseException): String {
		val status = ex.statusCode.value()
		return when (status) {
			400 -> "LinkedIn rejected the authorization code (expired or already used)."
			401 -> "LinkedIn authentication failed (check client credentials in /api/settings)."
			403 -> "LinkedIn refused access (scopes or app permissions)."
			else -> "LinkedIn OAuth HTTP $status"
		}
	}

	companion object {
		private const val AUTHORIZATION_URL = "https://www.linkedin.com/oauth/v2/authorization"
		private const val TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken"
		private val STATE_TTL: Duration = Duration.ofMinutes(10)
		private const val DEFAULT_EXPIRES_IN_SECONDS = 5_184_000L
	}
}

private data class PendingOAuth(
	val issuedAt: Instant,
	val accountId: String?,
	val label: String,
)

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
