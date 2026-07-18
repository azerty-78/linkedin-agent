package com.revolut.agent.service.publication

import com.revolut.agent.dto.settings.OrganizationSettingsResponse
import com.revolut.agent.model.settings.SchedulerFrequency
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Décision pure (testable) : fenêtre UTC + fréquence en jour calendaire org.
 */
object SchedulerDecision {

	const val WINDOW_MINUTES = 5L

	fun resolveZone(timezone: String): ZoneId =
		try {
			ZoneId.of(timezone)
		} catch (_: Exception) {
			ZoneOffset.UTC
		}

	fun isWithinPublishWindow(nowUtc: ZonedDateTime, publishHourUtc: Int): Boolean {
		val hour = publishHourUtc.coerceIn(0, 23)
		val target = nowUtc.toLocalDate().atTime(hour, 0).atZone(ZoneOffset.UTC)
		val start = target.minusMinutes(WINDOW_MINUTES)
		val end = target.plusMinutes(WINDOW_MINUTES)
		return !nowUtc.isBefore(start) && !nowUtc.isAfter(end)
	}

	/**
	 * @return true si le job doit tourner aujourd’hui (hors idempotence / enabled / fenêtre).
	 * @return Pair(shouldRun, anchorToPersist) — anchorToPersist non-null si EVERY_N_DAYS sans ancre.
	 */
	fun evaluateFrequency(
		settings: OrganizationSettingsResponse,
		nowUtc: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
	): FrequencyDecision {
		val zone = resolveZone(settings.timezone)
		val todayLocal = nowUtc.withZoneSameInstant(zone).toLocalDate()
		val dow = todayLocal.dayOfWeek.value // ISO 1=Mon … 7=Sun

		return when (settings.schedulerFrequency) {
			SchedulerFrequency.DAILY -> FrequencyDecision(shouldRun = true)
			SchedulerFrequency.WEEKDAYS -> FrequencyDecision(shouldRun = dow in 1..5)
			SchedulerFrequency.WEEKLY, SchedulerFrequency.CUSTOM_DAYS ->
				FrequencyDecision(shouldRun = dow in settings.schedulerDaysOfWeek)
			SchedulerFrequency.EVERY_N_DAYS -> {
				val interval = settings.schedulerIntervalDays.coerceIn(2, 30)
				val anchorRaw = settings.schedulerAnchorDate
				if (anchorRaw.isNullOrBlank()) {
					FrequencyDecision(shouldRun = true, anchorToPersist = todayLocal.toString())
				} else {
					val anchor = LocalDate.parse(anchorRaw)
					val days = ChronoUnit.DAYS.between(anchor, todayLocal)
					FrequencyDecision(shouldRun = days >= 0 && days % interval == 0L)
				}
			}
		}
	}

	data class FrequencyDecision(
		val shouldRun: Boolean,
		val anchorToPersist: String? = null,
	)
}
