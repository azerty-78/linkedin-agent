package com.revolut.agent.service.settings

import com.fasterxml.jackson.databind.ObjectMapper
import com.revolut.agent.config.EncryptionProperties
import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.linkedin.LinkedInAppListResponse
import com.revolut.agent.model.settings.OrganizationSettings
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import com.revolut.agent.service.linkedin.LinkedInAppService
import org.junit.jupiter.api.Assertions.assertEquals
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
 * PATCH partiel : clé IA seule / companyName seul sans exiger le reste.
 */
class PatchOrganizationSettingsTest {

	private lateinit var settingsRepository: OrganizationSettingsRepository
	private lateinit var connectionRepository: LinkedInConnectionRepository
	private lateinit var linkedInAppService: LinkedInAppService
	private lateinit var encryptionService: EncryptionService
	private lateinit var service: OrganizationSettingsService
	private val stored = AtomicReference<OrganizationSettings>()
	private val mapper = ObjectMapper()

	@BeforeEach
	fun setUp() {
		settingsRepository = mock(OrganizationSettingsRepository::class.java)
		connectionRepository = mock(LinkedInConnectionRepository::class.java)
		linkedInAppService = mock(LinkedInAppService::class.java)

		val encryptionProperties = EncryptionProperties(StandardEnvironment())
		encryptionProperties.key = Base64.getEncoder().encodeToString(ByteArray(32) { 3 })
		encryptionProperties.init()
		encryptionService = EncryptionService(encryptionProperties)

		stored.set(
			OrganizationSettings(
				id = "settings-1",
				companyName = "",
				contentTopic = "",
			),
		)

		`when`(settingsRepository.findFirstByOrderByUpdatedAtDesc()).thenAnswer {
			Mono.just(stored.get())
		}
		`when`(settingsRepository.save(any(OrganizationSettings::class.java))).thenAnswer { inv ->
			val saved = inv.getArgument<OrganizationSettings>(0)
			stored.set(saved)
			Mono.just(saved)
		}
		`when`(linkedInAppService.listApps()).thenReturn(
			Mono.just(LinkedInAppListResponse(apps = emptyList(), maxApps = 3, activeAppId = null)),
		)

		service = OrganizationSettingsService(
			organizationSettingsRepository = settingsRepository,
			linkedInConnectionRepository = connectionRepository,
			encryptionService = encryptionService,
			linkedInProperties = LinkedInProperties(),
			linkedInAppService = linkedInAppService,
		)
	}

	@Test
	fun `PATCH geminiApiKey alone succeeds without companyName`() {
		val body = mapper.readTree("""{"geminiApiKey":"AIza-test-key-zzzz","geminiModel":"gemini-2.0-flash"}""")

		StepVerifier.create(service.patchSettings(body, "admin@test.com"))
			.assertNext { response ->
				assertEquals("****zzzz", response.geminiKeyMasked)
				assertTrue(response.geminiModel.contains("gemini"))
				assertEquals("", response.companyName)
			}
			.verifyComplete()

		assertTrue(stored.get().encryptedGeminiKey!!.contains(":"))
	}

	@Test
	fun `PATCH companyName alone succeeds without contentTopic`() {
		val body = mapper.readTree("""{"companyName":"Revlut"}""")

		StepVerifier.create(service.patchSettings(body, "admin@test.com"))
			.assertNext { response ->
				assertEquals("Revlut", response.companyName)
				assertEquals("", response.contentTopic)
			}
			.verifyComplete()
	}

	@Test
	fun `PATCH omits leave other fields untouched`() {
		stored.set(
			OrganizationSettings(
				id = "settings-1",
				companyName = "KOBE",
				contentTopic = "tips",
				publishHourUtc = 11,
			),
		)

		val body = mapper.readTree("""{"schedulerEnabled":true}""")

		StepVerifier.create(service.patchSettings(body, "admin@test.com"))
			.assertNext {
				assertEquals("KOBE", it.companyName)
				assertEquals("tips", it.contentTopic)
				assertEquals(11, it.publishHourUtc)
				assertTrue(it.schedulerEnabled)
			}
			.verifyComplete()
	}

	@Test
	fun `PATCH blank companyName when present returns 400`() {
		val body = mapper.readTree("""{"companyName":"   "}""")

		StepVerifier.create(service.patchSettings(body, "admin@test.com"))
			.expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("companyName") }
			.verify()
	}
}
