package com.revolut.agent.service.settings

import com.revolut.agent.config.EncryptionProperties
import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.linkedin.LinkedInAppListResponse
import com.revolut.agent.dto.linkedin.LinkedInAppResponse
import com.revolut.agent.dto.settings.UpdateOrganizationSettingsRequest
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.OrganizationSettings
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import com.revolut.agent.service.linkedin.LinkedInAppService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.env.StandardEnvironment
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/**
 * Credentials LinkedIn ne sont plus écrits via PUT /api/settings (multi-apps).
 * GET settings peut overlay l’app active.
 */
class LinkedInCredentialsCrudTest {

	private lateinit var settingsRepository: OrganizationSettingsRepository
	private lateinit var connectionRepository: LinkedInConnectionRepository
	private lateinit var linkedInAppService: LinkedInAppService
	private lateinit var encryptionService: EncryptionService
	private lateinit var service: OrganizationSettingsService
	private val stored = AtomicReference<OrganizationSettings>()

	@BeforeEach
	fun setUp() {
		settingsRepository = mock(OrganizationSettingsRepository::class.java)
		connectionRepository = mock(LinkedInConnectionRepository::class.java)
		linkedInAppService = mock(LinkedInAppService::class.java)

		val encryptionProperties = EncryptionProperties(StandardEnvironment())
		encryptionProperties.key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
		encryptionProperties.init()
		encryptionService = EncryptionService(encryptionProperties)

		stored.set(OrganizationSettings(id = "settings-1", companyName = "KOBE", contentTopic = "tips"))

		`when`(settingsRepository.findFirstByOrderByUpdatedAtDesc()).thenAnswer {
			Mono.just(stored.get())
		}
		`when`(settingsRepository.save(any(OrganizationSettings::class.java))).thenAnswer { inv ->
			val saved = inv.getArgument<OrganizationSettings>(0)
			stored.set(saved)
			Mono.just(saved)
		}
		`when`(connectionRepository.findById(any(String::class.java))).thenReturn(Mono.empty())
		`when`(linkedInAppService.listApps()).thenReturn(
			Mono.just(LinkedInAppListResponse(apps = emptyList(), maxApps = 3, activeAppId = null)),
		)
		`when`(linkedInAppService.getActiveCredentials()).thenReturn(
			Mono.error(IllegalStateException("Aucune application LinkedIn active")),
		)
		`when`(linkedInAppService.hasActiveAppWithSecret()).thenReturn(Mono.just(false))

		service = OrganizationSettingsService(
			organizationSettingsRepository = settingsRepository,
			linkedInConnectionRepository = connectionRepository,
			encryptionService = encryptionService,
			linkedInProperties = LinkedInProperties(),
			linkedInAppService = linkedInAppService,
		)
	}

	@Test
	fun `PUT settings ignores linkedIn client id and secret writes`() {
		StepVerifier.create(
			service.updateSettings(
				baseRequest(
					linkedInClientId = "client-aaa",
					linkedInClientSecret = "secret-plain-1111",
				),
				"admin@test.com",
			),
		)
			.assertNext { response ->
				assertNull(response.linkedInClientId)
				assertEquals("", response.linkedInClientSecretMasked)
			}
			.verifyComplete()

		assertNull(stored.get().linkedInClientId)
		assertNull(stored.get().encryptedLinkedInClientSecret)
	}

	@Test
	fun `GET settings overlays active LinkedIn app credentials`() {
		`when`(linkedInAppService.listApps()).thenReturn(
			Mono.just(
				LinkedInAppListResponse(
					apps = listOf(
						LinkedInAppResponse(
							id = "app-1",
							label = "Prod",
							clientId = "clientActive",
							clientSecretMasked = "****cret",
							isActive = true,
							createdAt = Instant.parse("2026-07-18T10:00:00Z"),
							updatedAt = Instant.parse("2026-07-18T10:00:00Z"),
						),
					),
					maxApps = 3,
					activeAppId = "app-1",
				),
			),
		)

		StepVerifier.create(service.getSettings())
			.assertNext {
				assertEquals("clientActive", it.linkedInClientId)
				assertEquals("****cret", it.linkedInClientSecretMasked)
			}
			.verifyComplete()
	}

	@Test
	fun `getLinkedInAppCredentials delegates to LinkedInAppService`() {
		`when`(linkedInAppService.getActiveCredentials()).thenReturn(
			Mono.just(
				LinkedInAppCredentials(
					clientId = "from-app",
					clientSecret = "secret",
					redirectUri = "http://localhost:8080/api/linkedin/callback",
					oauthScopes = "openid",
				),
			),
		)

		StepVerifier.create(service.getLinkedInAppCredentials())
			.assertNext {
				assertEquals("from-app", it.clientId)
				assertEquals("secret", it.clientSecret)
			}
			.verifyComplete()
	}

	private fun baseRequest(
		linkedInClientId: String? = null,
		linkedInClientSecret: String? = null,
		clearLinkedInClientSecret: Boolean? = null,
	) = UpdateOrganizationSettingsRequest(
		companyName = "KOBE",
		contentTopic = "tips",
		publishHourUtc = 9,
		timezone = "Africa/Douala",
		activeLlmProvider = LlmProvider.GEMINI,
		linkedInClientId = linkedInClientId,
		linkedInClientSecret = linkedInClientSecret,
		clearLinkedInClientSecret = clearLinkedInClientSecret,
	)
}
