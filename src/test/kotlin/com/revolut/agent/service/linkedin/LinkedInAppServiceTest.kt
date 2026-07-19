package com.revolut.agent.service.linkedin

import com.revolut.agent.config.EncryptionProperties
import com.revolut.agent.config.LinkedInProperties
import com.revolut.agent.dto.linkedin.CreateLinkedInAppRequest
import com.revolut.agent.exception.linkedin.LinkedInAppLimitException
import com.revolut.agent.model.linkedin.LinkedInApp
import com.revolut.agent.model.linkedin.LinkedInAppAuditEvent
import com.revolut.agent.repository.linkedin.LinkedInAppAuditRepository
import com.revolut.agent.repository.linkedin.LinkedInAppRepository
import com.revolut.agent.repository.linkedin.LinkedInConnectionRepository
import com.revolut.agent.repository.settings.OrganizationSettingsRepository
import com.revolut.agent.service.settings.EncryptionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.env.StandardEnvironment
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class LinkedInAppServiceTest {

	private lateinit var appRepository: LinkedInAppRepository
	private lateinit var auditRepository: LinkedInAppAuditRepository
	private lateinit var connectionRepository: LinkedInConnectionRepository
	private lateinit var settingsRepository: OrganizationSettingsRepository
	private lateinit var encryptionService: EncryptionService
	private lateinit var service: LinkedInAppService

	private val apps = CopyOnWriteArrayList<LinkedInApp>()
	private val audits = CopyOnWriteArrayList<LinkedInAppAuditEvent>()
	private val idSeq = AtomicInteger(1)

	@BeforeEach
	fun setUp() {
		apps.clear()
		audits.clear()
		idSeq.set(1)

		appRepository = mock(LinkedInAppRepository::class.java)
		auditRepository = mock(LinkedInAppAuditRepository::class.java)
		connectionRepository = mock(LinkedInConnectionRepository::class.java)
		settingsRepository = mock(OrganizationSettingsRepository::class.java)

		val encryptionProperties = EncryptionProperties(StandardEnvironment())
		encryptionProperties.key = Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
		encryptionProperties.init()
		encryptionService = EncryptionService(encryptionProperties)

		`when`(appRepository.countByOrganizationId(LinkedInApp.DEFAULT_ORGANIZATION_ID)).thenAnswer {
			Mono.just(apps.size.toLong())
		}
		`when`(appRepository.findAllByOrganizationIdOrderByCreatedAtAsc(LinkedInApp.DEFAULT_ORGANIZATION_ID))
			.thenAnswer { Flux.fromIterable(apps.sortedBy { it.createdAt }) }
		`when`(appRepository.existsByOrganizationIdAndClientId(anyString(), anyString())).thenAnswer { inv ->
			val clientId = inv.getArgument<String>(1)
			Mono.just(apps.any { it.clientId == clientId })
		}
		`when`(appRepository.findByIdAndOrganizationId(anyString(), anyString())).thenAnswer { inv ->
			val id = inv.getArgument<String>(0)
			Mono.justOrEmpty(apps.firstOrNull { it.id == id })
		}
		`when`(appRepository.findByOrganizationIdAndIsActiveTrue(anyString())).thenAnswer {
			Mono.justOrEmpty(apps.firstOrNull { it.isActive })
		}
		`when`(appRepository.save(any(LinkedInApp::class.java))).thenAnswer { inv ->
			val app = inv.getArgument<LinkedInApp>(0)
			val withId = if (app.id == null) app.copy(id = "app-${idSeq.getAndIncrement()}") else app
			apps.removeIf { it.id == withId.id }
			apps.add(withId)
			Mono.just(withId)
		}
		`when`(appRepository.delete(any(LinkedInApp::class.java))).thenAnswer { inv ->
			val app = inv.getArgument<LinkedInApp>(0)
			apps.removeIf { it.id == app.id }
			Mono.empty<Void>()
		}
		`when`(auditRepository.save(any(LinkedInAppAuditEvent::class.java))).thenAnswer { inv ->
			val event = inv.getArgument<LinkedInAppAuditEvent>(0)
			val withId = event.copy(id = "audit-${audits.size + 1}")
			audits.add(withId)
			Mono.just(withId)
		}
		`when`(settingsRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Mono.empty())

		service = LinkedInAppService(
			linkedInAppRepository = appRepository,
			linkedInAppAuditRepository = auditRepository,
			linkedInConnectionRepository = connectionRepository,
			organizationSettingsRepository = settingsRepository,
			encryptionService = encryptionService,
			linkedInProperties = LinkedInProperties(),
		)
	}

	@Test
	fun `first app is active by default`() {
		StepVerifier.create(
			service.createApp(
				CreateLinkedInAppRequest(label = "Prod", clientId = "clientid01", clientSecret = "secret-aaaa"),
				actorEmail = "admin@test.com",
			),
		)
			.assertNext { list ->
				assertEquals(1, list.apps.size)
				assertTrue(list.apps[0].isActive)
				assertEquals("Prod", list.apps[0].label)
				assertEquals("****aaaa", list.apps[0].clientSecretMasked)
				assertEquals(list.apps[0].id, list.activeAppId)
			}
			.verifyComplete()

		assertEquals(1, audits.size)
		assertTrue(audits[0].summary.contains("créée"))
	}

	@Test
	fun `fourth create returns 409 limit`() {
		repeat(3) { i ->
			service.createApp(
				CreateLinkedInAppRequest(clientId = "clientid0$i", clientSecret = "secret"),
				actorEmail = "a@b.c",
			).block()
		}

		StepVerifier.create(
			service.createApp(
				CreateLinkedInAppRequest(clientId = "clientid99", clientSecret = "secret"),
				actorEmail = "a@b.c",
			),
		)
			.expectError(LinkedInAppLimitException::class.java)
			.verify()
	}

	@Test
	fun `activate is exclusive`() {
		val first = service.createApp(
			CreateLinkedInAppRequest(label = "A", clientId = "clientidaa", clientSecret = "s1"),
			"a@b.c",
		).block()!!
		val second = service.createApp(
			CreateLinkedInAppRequest(label = "B", clientId = "clientidbb", clientSecret = "s2"),
			"a@b.c",
		).block()!!

		val secondId = second.apps.first { it.label == "B" }.id
		assertEquals(first.apps.first { it.label == "A" }.id, first.activeAppId)
		assertEquals(first.activeAppId, second.activeAppId) // encore A après 2e create

		StepVerifier.create(service.activateApp(secondId, "a@b.c"))
			.assertNext { list ->
				assertEquals(secondId, list.activeAppId)
				assertEquals(1, list.apps.count { it.isActive })
				assertTrue(list.apps.first { it.id == secondId }.isActive)
			}
			.verifyComplete()
	}

	@Test
	fun `secret never appears in audit summary`() {
		service.createApp(
			CreateLinkedInAppRequest(clientId = "clientidxx", clientSecret = "super-secret-zzzz"),
			"a@b.c",
		).block()

		assertTrue(audits.none { it.summary.contains("super-secret") })
		assertTrue(audits.none { it.summary.contains("zzzz") })
	}
}
