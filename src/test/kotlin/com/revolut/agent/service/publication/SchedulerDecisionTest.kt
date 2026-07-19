package com.revolut.agent.service.publication

import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.model.settings.BrandTone
import com.revolut.agent.model.settings.ContentLanguage
import com.revolut.agent.model.settings.EmojiPolicy
import com.revolut.agent.model.settings.LlmProvider
import com.revolut.agent.model.settings.PostLengthPreference
import com.revolut.agent.model.settings.SchedulerFrequency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SchedulerDecisionTest {

	@Test
	fun `WEEKDAYS skips weekend`() {
		// 2026-07-18 = Saturday
		val saturday = ZonedDateTime.of(2026, 7, 18, 9, 0, 0, 0, ZoneOffset.UTC)
		val decision = SchedulerDecision.evaluateFrequency(
			settings(frequency = SchedulerFrequency.WEEKDAYS),
			saturday,
		)
		assertFalse(decision.shouldRun)

		val monday = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneOffset.UTC)
		assertTrue(SchedulerDecision.evaluateFrequency(settings(SchedulerFrequency.WEEKDAYS), monday).shouldRun)
	}

	@Test
	fun `CUSTOM_DAYS only listed days`() {
		val monday = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneOffset.UTC) // ISO 1
		val tuesday = ZonedDateTime.of(2026, 7, 21, 9, 0, 0, 0, ZoneOffset.UTC)
		val s = settings(SchedulerFrequency.CUSTOM_DAYS, days = listOf(1, 3))
		assertTrue(SchedulerDecision.evaluateFrequency(s, monday).shouldRun)
		assertFalse(SchedulerDecision.evaluateFrequency(s, tuesday).shouldRun)
	}

	@Test
	fun `EVERY_N_DAYS without anchor persists today and runs`() {
		val day = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneOffset.UTC)
		val decision = SchedulerDecision.evaluateFrequency(
			settings(SchedulerFrequency.EVERY_N_DAYS, interval = 3, anchor = null),
			day,
		)
		assertTrue(decision.shouldRun)
		assertEquals("2026-07-20", decision.anchorToPersist)
	}

	@Test
	fun `EVERY_N_DAYS respects interval from anchor`() {
		val s = settings(SchedulerFrequency.EVERY_N_DAYS, interval = 3, anchor = "2026-07-20")
		val d0 = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneOffset.UTC)
		val d1 = ZonedDateTime.of(2026, 7, 21, 9, 0, 0, 0, ZoneOffset.UTC)
		val d3 = ZonedDateTime.of(2026, 7, 23, 9, 0, 0, 0, ZoneOffset.UTC)
		assertTrue(SchedulerDecision.evaluateFrequency(s, d0).shouldRun)
		assertFalse(SchedulerDecision.evaluateFrequency(s, d1).shouldRun)
		assertTrue(SchedulerDecision.evaluateFrequency(s, d3).shouldRun)
	}

	@Test
	fun `publish window is plus or minus 5 minutes UTC`() {
		val now = ZonedDateTime.of(2026, 7, 20, 9, 3, 0, 0, ZoneOffset.UTC)
		assertTrue(SchedulerDecision.isWithinPublishWindow(now, 9))
		assertFalse(SchedulerDecision.isWithinPublishWindow(now.withHour(10).withMinute(0), 9))
	}

	@Test
	fun `disabled path is caller responsibility — DAILY always ok on frequency`() {
		assertTrue(
			SchedulerDecision.evaluateFrequency(
				settings(SchedulerFrequency.DAILY),
				ZonedDateTime.of(2026, 7, 19, 12, 0, 0, 0, ZoneOffset.UTC),
			).shouldRun,
		)
	}

	@Test
	fun `nextScheduledAt is null when scheduler disabled`() {
		assertNull(
			SchedulerDecision.computeNextScheduledAt(
				settings(SchedulerFrequency.DAILY, enabled = false),
				Instant.parse("2026-07-20T08:00:00Z"),
			),
		)
	}

	@Test
	fun `nextScheduledAt DAILY is today publish hour if window not passed`() {
		val next = SchedulerDecision.computeNextScheduledAt(
			settings(SchedulerFrequency.DAILY),
			Instant.parse("2026-07-20T08:00:00Z"),
		)
		assertEquals(Instant.parse("2026-07-20T09:00:00Z"), next)
	}

	@Test
	fun `nextScheduledAt DAILY skips today after window end`() {
		val next = SchedulerDecision.computeNextScheduledAt(
			settings(SchedulerFrequency.DAILY),
			Instant.parse("2026-07-20T09:06:00Z"),
		)
		assertEquals(Instant.parse("2026-07-21T09:00:00Z"), next)
	}

	@Test
	fun `nextScheduledAt WEEKDAYS skips weekend`() {
		// Saturday 2026-07-18 08:00 → next Monday 20th 09:00
		val next = SchedulerDecision.computeNextScheduledAt(
			settings(SchedulerFrequency.WEEKDAYS),
			Instant.parse("2026-07-18T08:00:00Z"),
		)
		assertEquals(Instant.parse("2026-07-20T09:00:00Z"), next)
	}

	private fun settings(
		frequency: SchedulerFrequency,
		days: List<Int> = emptyList(),
		interval: Int = 2,
		anchor: String? = null,
		enabled: Boolean = true,
	) = OrganizationSettingsResponse(
		companyName = "KOBE",
		companyTagline = null,
		industry = null,
		websiteUrl = null,
		contentTopic = "tips",
		contentLanguage = ContentLanguage.auto,
		brandTone = BrandTone.PROFESSIONAL,
		targetAudience = null,
		brandGuidelines = null,
		defaultHashtags = null,
		contentPillars = null,
		topicsToAvoid = null,
		contentGoals = null,
		preferredFormats = emptyList(),
		callToActionPreference = null,
		emojiPolicy = EmojiPolicy.SPARSE,
		postLengthPreference = PostLengthPreference.MEDIUM,
		mustIncludeKeywords = null,
		examplePostStyle = null,
		publishHourUtc = 9,
		timezone = "UTC",
		activeLlmProvider = LlmProvider.GEMINI,
		openAiModel = "gpt-4.1-nano",
		anthropicModel = "claude-haiku-4-5",
		geminiModel = "gemini-3.5-flash",
		openAiKeyMasked = "",
		anthropicKeyMasked = "",
		geminiKeyMasked = "****abcd",
		linkedInOrganizationId = null,
		linkedInClientId = null,
		linkedInClientSecretMasked = "",
		activeLinkedInAccountId = null,
		schedulerEnabled = enabled,
		schedulerFrequency = frequency,
		schedulerDaysOfWeek = days,
		schedulerIntervalDays = interval,
		schedulerAnchorDate = anchor,
		updatedAt = Instant.parse("2026-07-18T12:00:00Z"),
	)
}
