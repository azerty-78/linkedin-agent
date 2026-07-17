package com.revolut.agent.service.settings

import com.revolut.agent.config.EncryptionProperties
import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.settings.UpdateOrganizationSettingsRequest
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.OrganizationSettings
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.env.StandardEnvironment
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/**
 * Vérifie Create / Read / Update / Delete des credentials LinkedIn en settings (DB chiffrée).
 */
class LinkedInCredentialsCrudTest {

	private lateinit var settingsRepository: OrganizationSettingsRepository
	private lateinit var connectionRepository: LinkedInConnectionRepository
	private lateinit var encryptionService: EncryptionService
	private lateinit var service: OrganizationSettingsService
	private val stored = AtomicReference<OrganizationSettings>()

	@BeforeEach
	fun setUp() {
		settingsRepository = mock(OrganizationSettingsRepository::class.java)
		connectionRepository = mock(LinkedInConnectionRepository::class.java)

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

		service = OrganizationSettingsService(
			organizationSettingsRepository = settingsRepository,
			linkedInConnectionRepository = connectionRepository,
			encryptionService = encryptionService,
			linkedInProperties = LinkedInProperties(),
		)
	}

	@Test
	fun `CRUD linkedIn credentials — create read update delete`() {
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
				assertEquals("client-aaa", response.linkedInClientId)
				assertTrue(response.linkedInClientSecretMasked.startsWith("****"))
			}
			.verifyComplete()

		assertTrue(stored.get().encryptedLinkedInClientSecret!!.contains(":"))

		StepVerifier.create(service.getSettings())
			.assertNext {
				assertEquals("client-aaa", it.linkedInClientId)
				assertEquals("****1111", it.linkedInClientSecretMasked)
			}
			.verifyComplete()

		StepVerifier.create(service.getLinkedInAppCredentials())
			.assertNext {
				assertEquals("client-aaa", it.clientId)
				assertEquals("secret-plain-1111", it.clientSecret)
				assertEquals("http://localhost:8080/api/linkedin/callback", it.redirectUri)
				assertTrue(it.oauthScopes.contains("w_organization_social"))
			}
			.verifyComplete()

		StepVerifier.create(
			service.updateSettings(
				baseRequest(linkedInClientId = "client-bbb", linkedInClientSecret = null),
				"admin@test.com",
			),
		)
			.assertNext {
				assertEquals("client-bbb", it.linkedInClientId)
				assertEquals("****1111", it.linkedInClientSecretMasked)
			}
			.verifyComplete()

		StepVerifier.create(
			service.updateSettings(
				baseRequest(linkedInClientSecret = "secret-plain-2222"),
				"admin@test.com",
			),
		)
			.assertNext { assertEquals("****2222", it.linkedInClientSecretMasked) }
			.verifyComplete()

		StepVerifier.create(
			service.updateSettings(
				baseRequest(
					linkedInClientId = "",
					clearLinkedInClientSecret = true,
				),
				"admin@test.com",
			),
		)
			.assertNext {
				assertNull(it.linkedInClientId)
				assertEquals("", it.linkedInClientSecretMasked)
			}
			.verifyComplete()

		assertNull(stored.get().linkedInClientId)
		assertNull(stored.get().encryptedLinkedInClientSecret)

		StepVerifier.create(service.getLinkedInAppCredentials())
			.expectErrorMatches { it is IllegalStateException && it.message!!.contains("client ID") }
			.verify()
	}

	@Test
	fun `empty secret without clear flag keeps existing`() {
		StepVerifier.create(
			service.updateSettings(
				baseRequest(linkedInClientId = "c1", linkedInClientSecret = "keep-me-zzzz"),
				"admin@test.com",
			),
		).expectNextCount(1).verifyComplete()

		StepVerifier.create(
			service.updateSettings(
				baseRequest(linkedInClientSecret = "   ", clearLinkedInClientSecret = false),
				"admin@test.com",
			),
		)
			.assertNext { assertEquals("****zzzz", it.linkedInClientSecretMasked) }
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
