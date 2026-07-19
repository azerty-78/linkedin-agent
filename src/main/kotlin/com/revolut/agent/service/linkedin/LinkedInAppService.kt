package com.revolut.agent.service.linkedin

import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.linkedin.CreateLinkedInAppRequest
import com.revolut.agent.dto.linkedin.LinkedInAppAuditEventResponse
import com.revolut.agent.dto.linkedin.LinkedInAppAuditExportResponse
import com.revolut.agent.dto.linkedin.LinkedInAppAuditListResponse
import com.revolut.agent.dto.linkedin.LinkedInAppHealthCheck
import com.revolut.agent.dto.linkedin.LinkedInAppHealthResponse
import com.revolut.agent.dto.linkedin.LinkedInAppListResponse
import com.revolut.agent.dto.linkedin.LinkedInAppResponse
import com.revolut.agent.dto.linkedin.LinkedInAppVerifyResponse
import com.revolut.agent.dto.linkedin.UpdateLinkedInAppRequest
import com.revolut.agent.exception.linkedin.LinkedInAppConflictException
import com.revolut.agent.exception.linkedin.LinkedInAppLimitException
import com.revolut.agent.model.linkedin.LinkedInApp
import com.revolut.agent.model.linkedin.LinkedInAppAuditAction
import com.revolut.agent.model.linkedin.LinkedInAppAuditEvent
import com.revolut.agent.model.linkedin.LinkedInConnection
import com.revolut.agent.model.linkedin.LinkedInConnectionStatus
import com.revolut.agent.repository.linkedin.LinkedInAppAuditRepository
import com.revolut.agent.repository.linkedin.LinkedInAppRepository
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import com.revolut.agent.service.settings.EncryptionService
import com.revolut.agent.service.settings.LinkedInAppCredentials
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

@Service
class LinkedInAppService(
	private val linkedInAppRepository: LinkedInAppRepository,
	private val linkedInAppAuditRepository: LinkedInAppAuditRepository,
	private val linkedInConnectionRepository: LinkedInConnectionRepository,
	private val organizationSettingsRepository: OrganizationSettingsRepository,
	private val encryptionService: EncryptionService,
	private val linkedInProperties: LinkedInProperties,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun listApps(organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID): Mono<LinkedInAppListResponse> =
		linkedInAppRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId)
			.map(::toResponse)
			.collectList()
			.map { apps ->
				LinkedInAppListResponse(
					apps = apps,
					maxApps = LinkedInApp.MAX_APPS,
					activeAppId = apps.firstOrNull { it.isActive }?.id,
				)
			}

	fun createApp(
		request: CreateLinkedInAppRequest,
		actorEmail: String?,
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<LinkedInAppListResponse> {
		val clientId = request.clientId.trim()
		val secret = request.clientSecret.trim()
		if (secret.isEmpty()) {
			return Mono.error(IllegalArgumentException("clientSecret is required"))
		}

		return linkedInAppRepository.countByOrganizationId(organizationId)
			.flatMap { count ->
				if (count >= LinkedInApp.MAX_APPS) {
					Mono.error(LinkedInAppLimitException())
				} else {
					linkedInAppRepository.existsByOrganizationIdAndClientId(organizationId, clientId)
						.flatMap { exists ->
							if (exists) {
								Mono.error(LinkedInAppConflictException("clientId déjà enregistré pour cette organisation"))
							} else {
								val now = Instant.now()
								val label = request.label?.trim()?.takeIf { it.isNotEmpty() }
									?: "App ${count + 1}"
								val isFirst = count == 0L
								val app = LinkedInApp(
									organizationId = organizationId,
									label = label,
									clientId = clientId,
									encryptedClientSecret = encryptionService.encrypt(secret),
									isActive = isFirst,
									createdAt = now,
									updatedAt = now,
								)
								linkedInAppRepository.save(app)
									.flatMap { saved ->
										appendAudit(
											organizationId = organizationId,
											appId = saved.id,
											appLabel = saved.label,
											action = LinkedInAppAuditAction.CREATED,
											summary = "Application « ${saved.label} » créée",
											actorEmail = actorEmail,
										).thenReturn(saved)
									}
									.then(listApps(organizationId))
							}
						}
				}
			}
	}

	fun updateApp(
		appId: String,
		request: UpdateLinkedInAppRequest,
		actorEmail: String?,
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<LinkedInAppListResponse> =
		findOwned(appId, organizationId)
			.flatMap { existing ->
				val nextClientId = request.clientId?.trim()?.takeIf { it.isNotEmpty() } ?: existing.clientId
				val clearSecret = request.clearClientSecret == true
				val newSecret = request.clientSecret?.trim()?.takeIf { it.isNotEmpty() }
				val nextLabel = request.label?.trim()?.takeIf { it.isNotEmpty() } ?: existing.label

				val clientIdCheck: Mono<Boolean> =
					if (nextClientId != existing.clientId) {
						linkedInAppRepository.existsByOrganizationIdAndClientIdAndIdNot(
							organizationId,
							nextClientId,
							appId,
						)
					} else {
						Mono.just(false)
					}

				clientIdCheck.flatMap { conflict ->
					if (conflict) {
						return@flatMap Mono.error(
							LinkedInAppConflictException("clientId déjà enregistré pour cette organisation"),
						)
					}

					val changed = mutableListOf<String>()
					if (nextLabel != existing.label) changed += "label"
					if (nextClientId != existing.clientId) changed += "clientId"

					val nextEncrypted = when {
						clearSecret -> {
							changed += "clientSecret"
							null
						}
						newSecret != null -> {
							changed += "clientSecret"
							encryptionService.encrypt(newSecret)
						}
						else -> existing.encryptedClientSecret
					}

					if (changed.isEmpty()) {
						return@flatMap listApps(organizationId)
					}

					val updated = existing.copy(
						label = nextLabel,
						clientId = nextClientId,
						encryptedClientSecret = nextEncrypted,
						updatedAt = Instant.now(),
					)

					val auditAction = when {
						clearSecret -> LinkedInAppAuditAction.SECRET_CLEARED
						newSecret != null -> LinkedInAppAuditAction.SECRET_ROTATED
						else -> LinkedInAppAuditAction.UPDATED
					}
					val summary = when (auditAction) {
						LinkedInAppAuditAction.SECRET_CLEARED ->
							"Secret de « ${updated.label} » effacé"
						LinkedInAppAuditAction.SECRET_ROTATED ->
							"Secret de « ${updated.label} » renouvelé"
						else ->
							"Application « ${updated.label} » mise à jour (${changed.joinToString(", ")})"
					}

					linkedInAppRepository.save(updated)
						.flatMap {
							appendAudit(
								organizationId = organizationId,
								appId = it.id,
								appLabel = it.label,
								action = auditAction,
								summary = summary,
								actorEmail = actorEmail,
								changedFields = changed,
							)
						}
						.then(listApps(organizationId))
				}
			}

	fun activateApp(
		appId: String,
		actorEmail: String?,
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<LinkedInAppListResponse> =
		findOwned(appId, organizationId)
			.flatMap { target ->
				linkedInAppRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId)
					.collectList()
					.flatMap { all ->
						Flux.fromIterable(all)
							.concatMap { app ->
								linkedInAppRepository.save(
									app.copy(
										isActive = app.id == target.id,
										updatedAt = Instant.now(),
									),
								)
							}
							.collectList()
							.flatMap { saved ->
								appendAudit(
									organizationId = organizationId,
									appId = target.id,
									appLabel = target.label,
									action = LinkedInAppAuditAction.ACTIVATED,
									summary = "Application « ${target.label} » définie comme active",
									actorEmail = actorEmail,
								).thenReturn(
									LinkedInAppListResponse(
										apps = saved.sortedBy { it.createdAt }.map(::toResponse),
										maxApps = LinkedInApp.MAX_APPS,
										activeAppId = saved.firstOrNull { it.isActive }?.id,
									),
								)
							}
					}
			}

	fun deleteApp(
		appId: String,
		actorEmail: String?,
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<Void> =
		findOwned(appId, organizationId)
			.flatMap { target ->
				val wasActive = target.isActive
				linkedInAppRepository.delete(target)
					.then(
						appendAudit(
							organizationId = organizationId,
							appId = target.id,
							appLabel = target.label,
							action = LinkedInAppAuditAction.DELETED,
							summary = "Application « ${target.label} » supprimée",
							actorEmail = actorEmail,
						),
					)
					.then(
						if (wasActive) {
							autoActivateNewest(organizationId)
						} else {
							Mono.empty()
						},
					)
			}

	fun listAudit(
		page: Int,
		size: Int,
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<LinkedInAppAuditListResponse> {
		val safePage = page.coerceAtLeast(0)
		val safeSize = size.coerceIn(1, 50)
		val skip = safePage.toLong() * safeSize
		return linkedInAppAuditRepository.countByOrganizationId(organizationId)
			.zipWith(
				linkedInAppAuditRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId)
					.skip(skip)
					.take(safeSize.toLong())
					.map(::toAuditResponse)
					.collectList(),
			)
			.map { tuple ->
				LinkedInAppAuditListResponse(
					events = tuple.t2,
					total = tuple.t1,
					page = safePage,
					size = safeSize,
				)
			}
	}

	fun exportAudit(
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<LinkedInAppAuditExportResponse> =
		linkedInAppAuditRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId)
			.map(::toAuditResponse)
			.collectList()
			.map { LinkedInAppAuditExportResponse(events = it) }

	/**
	 * Diagnostic app active + token OAuth + Page URN.
	 * N’obtient **pas** de token — pour ça : `POST /api/linkedin/accounts/connect`.
	 * Status checks : `ok` | `warn` | `fail` (minuscules, contrat front).
	 */
	fun health(
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<LinkedInAppHealthResponse> =
		linkedInAppRepository.findByOrganizationIdAndIsActiveTrue(organizationId)
			.map { OptionalApp(it) }
			.defaultIfEmpty(OptionalApp(null))
			.zipWith(
				organizationSettingsRepository.findFirstByOrderByUpdatedAtDesc()
					.map { it.activeLinkedInAccountId.orEmpty() }
					.defaultIfEmpty(""),
			)
			.flatMap { tuple ->
				val activeApp = tuple.t1.app
				val accountId = tuple.t2.takeIf { it.isNotBlank() }
				buildHealthChecks(activeApp, accountId)
			}

	private fun buildHealthChecks(
		activeApp: LinkedInApp?,
		accountId: String?,
	): Mono<LinkedInAppHealthResponse> {
		val checks = mutableListOf<LinkedInAppHealthCheck>()

		if (activeApp == null) {
			checks += LinkedInAppHealthCheck("ACTIVE_APP", STATUS_FAIL, "Aucune application active")
			checks += LinkedInAppHealthCheck("CREDENTIALS_PRESENT", STATUS_FAIL, "Client ID et Secret absents")
			checks += LinkedInAppHealthCheck("CLIENT_ID_FORMAT", STATUS_FAIL, "Pas de Client ID à valider")
			checks += LinkedInAppHealthCheck(
				"TOKEN_VALID",
				STATUS_FAIL,
				"Aucun token OAuth — connectez une Page",
			)
			checks += LinkedInAppHealthCheck(
				"PAGE_URN_SET",
				STATUS_FAIL,
				"Aucun compte LinkedIn / Page liée",
			)
			return Mono.just(
				LinkedInAppHealthResponse(
					ok = false,
					activeAppId = null,
					activeAppLabel = null,
					checks = checks,
					tokenExpiresAt = null,
					tokenExpiringSoon = false,
				),
			)
		}

		checks += LinkedInAppHealthCheck(
			"ACTIVE_APP",
			STATUS_OK,
			"App active « ${activeApp.label} »",
		)

		val secretPresent = !activeApp.encryptedClientSecret.isNullOrBlank()
		val secretReadable = secretPresent &&
			encryptionService.tryDecrypt(activeApp.encryptedClientSecret!!) != null
		checks += when {
			!secretPresent ->
				LinkedInAppHealthCheck("CREDENTIALS_PRESENT", STATUS_FAIL, "Secret manquant")
			!secretReadable ->
				LinkedInAppHealthCheck(
					"CREDENTIALS_PRESENT",
					STATUS_FAIL,
					"Secret illisible (ENCRYPTION_KEY ?)",
				)
			activeApp.clientId.isBlank() ->
				LinkedInAppHealthCheck("CREDENTIALS_PRESENT", STATUS_FAIL, "Client ID manquant")
			else ->
				LinkedInAppHealthCheck(
					"CREDENTIALS_PRESENT",
					STATUS_OK,
					"Client ID et Secret présents",
				)
		}

		val clientId = activeApp.clientId.trim()
		checks += when {
			CLIENT_ID_REGEX.matches(clientId) ->
				LinkedInAppHealthCheck("CLIENT_ID_FORMAT", STATUS_OK, "Format Client ID valide")
			clientId.isBlank() ->
				LinkedInAppHealthCheck("CLIENT_ID_FORMAT", STATUS_FAIL, "Client ID vide")
			else ->
				LinkedInAppHealthCheck("CLIENT_ID_FORMAT", STATUS_WARN, "Format Client ID douteux")
		}

		val accountMono: Mono<OptionalConn> =
			if (accountId == null) {
				Mono.just(OptionalConn(null))
			} else {
				linkedInConnectionRepository.findById(accountId)
					.map { OptionalConn(it) }
					.defaultIfEmpty(OptionalConn(null))
			}

		return accountMono.map { wrapped ->
			val account = wrapped.connection
			var tokenExpiresAt: Instant? = null
			var tokenExpiringSoon = false

			when {
				account == null -> {
					checks += LinkedInAppHealthCheck(
						"TOKEN_VALID",
						STATUS_FAIL,
						"Aucun token OAuth — connectez une Page",
					)
					checks += LinkedInAppHealthCheck(
						"PAGE_URN_SET",
						STATUS_FAIL,
						"Aucun compte LinkedIn / Page liée",
					)
				}
				account.status != LinkedInConnectionStatus.CONNECTED ||
					account.encryptedAccessToken.isNullOrBlank() -> {
					checks += LinkedInAppHealthCheck(
						"TOKEN_VALID",
						STATUS_FAIL,
						"Aucun token OAuth — reconnectez le compte",
					)
					checks += pageUrnCheck(account.linkedInOrganizationUrn)
				}
				else -> {
					tokenExpiresAt = account.accessTokenExpiresAt
					when {
						tokenExpiresAt == null ->
							checks += LinkedInAppHealthCheck(
								"TOKEN_VALID",
								STATUS_WARN,
								"Token présent — expiration inconnue",
							)
						tokenExpiresAt.isBefore(Instant.now()) ->
							checks += LinkedInAppHealthCheck(
								"TOKEN_VALID",
								STATUS_FAIL,
								"Token OAuth expiré — reconnectez le compte",
							)
						Duration.between(Instant.now(), tokenExpiresAt).toDays() <= 7 -> {
							tokenExpiringSoon = true
							checks += LinkedInAppHealthCheck(
								"TOKEN_VALID",
								STATUS_WARN,
								"Token OAuth expire dans moins de 7 jours",
							)
						}
						else ->
							checks += LinkedInAppHealthCheck(
								"TOKEN_VALID",
								STATUS_OK,
								"Token OAuth valide",
							)
					}
					checks += pageUrnCheck(account.linkedInOrganizationUrn)
				}
			}

			LinkedInAppHealthResponse(
				ok = checks.none { it.status == STATUS_FAIL },
				activeAppId = activeApp.id,
				activeAppLabel = activeApp.label,
				checks = checks.toList(),
				tokenExpiresAt = tokenExpiresAt,
				tokenExpiringSoon = tokenExpiringSoon,
			)
		}
	}

	private fun pageUrnCheck(urn: String?): LinkedInAppHealthCheck =
		if (urn.isNullOrBlank()) {
			LinkedInAppHealthCheck(
				"PAGE_URN_SET",
				STATUS_FAIL,
				"Aucun compte LinkedIn / Page liée",
			)
		} else {
			LinkedInAppHealthCheck("PAGE_URN_SET", STATUS_OK, "URN Page configuré")
		}

	/**
	 * Soft-check Client ID + Secret (déchiffrable + format) — **pas** un login OAuth.
	 * `422` si secret manquant ; `404` si app hors scope.
	 */
	fun verifyApp(
		appId: String,
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<LinkedInAppVerifyResponse> =
		findOwned(appId, organizationId)
			.flatMap { app ->
				val id = requireNotNull(app.id)
				if (app.encryptedClientSecret.isNullOrBlank()) {
					return@flatMap Mono.error(
						IllegalStateException("LinkedIn app secret missing — impossible de vérifier"),
					)
				}
				val secret = encryptionService.tryDecrypt(app.encryptedClientSecret!!)
				if (secret == null) {
					return@flatMap Mono.just(
						LinkedInAppVerifyResponse(
							ok = false,
							appId = id,
							message = "Secret illisible (ENCRYPTION_KEY ?) — resaisissez le secret",
						),
					)
				}
				val clientId = app.clientId.trim()
				when {
					clientId.isBlank() ->
						Mono.just(
							LinkedInAppVerifyResponse(ok = false, appId = id, message = "Client ID manquant"),
						)
					!CLIENT_ID_REGEX.matches(clientId) ->
						Mono.just(
							LinkedInAppVerifyResponse(
								ok = false,
								appId = id,
								message = "Client ID invalide (format attendu : 8–64 alphanumériques)",
							),
						)
					else ->
						Mono.just(
							LinkedInAppVerifyResponse(
								ok = true,
								appId = id,
								message = "Credentials acceptés / format OK",
							),
						)
				}
			}

	/**
	 * Credentials de l’app active — utilisé par OAuth / publish.
	 */
	fun getActiveCredentials(
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<LinkedInAppCredentials> =
		linkedInAppRepository.findByOrganizationIdAndIsActiveTrue(organizationId)
			.switchIfEmpty(
				Mono.error(
					IllegalStateException(
						"Aucune application LinkedIn active. Créez-en une via POST /api/linkedin/apps.",
					),
				),
			)
			.flatMap { app ->
				val secretEncrypted = app.encryptedClientSecret
				if (secretEncrypted.isNullOrBlank()) {
					return@flatMap Mono.error(
						IllegalStateException(
							"Secret manquant sur l’application active « ${app.label} ». Mettez-le à jour via PUT /api/linkedin/apps/{id}.",
						),
					)
				}
				val secret = encryptionService.tryDecrypt(secretEncrypted)
					?: return@flatMap Mono.error(
						IllegalStateException(
							"Secret LinkedIn illisible (ENCRYPTION_KEY?). Re-saisissez le secret de l’app active.",
						),
					)
				Mono.just(
					LinkedInAppCredentials(
						clientId = app.clientId,
						clientSecret = secret,
						redirectUri = linkedInProperties.redirectUri,
						oauthScopes = linkedInProperties.oauthScopes,
					),
				)
			}

	fun hasActiveAppWithSecret(
		organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID,
	): Mono<Boolean> =
		linkedInAppRepository.findByOrganizationIdAndIsActiveTrue(organizationId)
			.map { !it.encryptedClientSecret.isNullOrBlank() }
			.defaultIfEmpty(false)

	fun countApps(organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID): Mono<Long> =
		linkedInAppRepository.countByOrganizationId(organizationId)

	fun activeAppId(organizationId: String = LinkedInApp.DEFAULT_ORGANIZATION_ID): Mono<String> =
		linkedInAppRepository.findByOrganizationIdAndIsActiveTrue(organizationId)
			.mapNotNull { it.id }
			.defaultIfEmpty("")

	private fun autoActivateNewest(organizationId: String): Mono<Void> =
		linkedInAppRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId)
			.collectList()
			.flatMap { remaining ->
				if (remaining.isEmpty()) {
					Mono.empty()
				} else {
					val newest = remaining.maxBy { it.createdAt }
					Flux.fromIterable(remaining)
						.flatMap { app ->
							val active = app.id == newest.id
							if (app.isActive == active) Mono.just(app)
							else linkedInAppRepository.save(app.copy(isActive = active, updatedAt = Instant.now()))
						}
						.then()
						.doOnSuccess {
							log.info(
								"Auto-activated LinkedIn app « {} » after delete of previous active",
								newest.label,
							)
						}
				}
			}

	private fun findOwned(appId: String, organizationId: String): Mono<LinkedInApp> =
		linkedInAppRepository.findByIdAndOrganizationId(appId, organizationId)
			.switchIfEmpty(Mono.error(NoSuchElementException("LinkedIn app not found: $appId")))

	private fun appendAudit(
		organizationId: String,
		appId: String?,
		appLabel: String?,
		action: LinkedInAppAuditAction,
		summary: String,
		actorEmail: String?,
		changedFields: List<String> = emptyList(),
	): Mono<LinkedInAppAuditEvent> =
		linkedInAppAuditRepository.save(
			LinkedInAppAuditEvent(
				organizationId = organizationId,
				appId = appId,
				appLabel = appLabel,
				action = action,
				summary = summary,
				actorEmail = actorEmail,
				changedFields = changedFields,
				createdAt = Instant.now(),
			),
		)

	private fun toResponse(app: LinkedInApp): LinkedInAppResponse {
		val masked = if (app.encryptedClientSecret.isNullOrBlank()) {
			""
		} else {
			encryptionService.tryDecrypt(app.encryptedClientSecret)?.let { encryptionService.mask(it) }.orEmpty()
		}
		return LinkedInAppResponse(
			id = requireNotNull(app.id),
			label = app.label,
			clientId = app.clientId,
			clientSecretMasked = masked,
			isActive = app.isActive,
			createdAt = app.createdAt,
			updatedAt = app.updatedAt,
		)
	}

	private fun toAuditResponse(event: LinkedInAppAuditEvent): LinkedInAppAuditEventResponse =
		LinkedInAppAuditEventResponse(
			id = event.id,
			appId = event.appId,
			appLabel = event.appLabel,
			action = event.action.name,
			summary = event.summary,
			actorEmail = event.actorEmail,
			createdAt = event.createdAt,
		)

	private data class OptionalApp(val app: LinkedInApp?)
	private data class OptionalConn(val connection: LinkedInConnection?)

	companion object {
		private const val STATUS_OK = "ok"
		private const val STATUS_WARN = "warn"
		private const val STATUS_FAIL = "fail"
		private val CLIENT_ID_REGEX = Regex("^[a-zA-Z0-9]{8,64}$")
	}
}
