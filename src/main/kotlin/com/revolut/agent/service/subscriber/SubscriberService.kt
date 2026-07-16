package com.revolut.agent.service

import com.revolut.agent.dto.CreateSubscriberRequest
import com.revolut.agent.dto.SubscriberListResponse
import com.revolut.agent.dto.SubscriberResponse
import com.revolut.agent.exception.SubscriberAlreadyActiveException
import com.revolut.agent.model.Subscriber
import com.revolut.agent.model.SubscriberStatus
import com.revolut.agent.repository.SubscriberRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

@Service
class SubscriberService(
	private val subscriberRepository: SubscriberRepository,
) {

	fun createSubscriber(request: CreateSubscriberRequest): Mono<SubscriberResponse> {
		val normalizedEmail = request.email.trim().lowercase()

		return subscriberRepository.findByEmail(normalizedEmail)
			.flatMap { existing ->
				when (existing.status) {
					SubscriberStatus.ACTIVE ->
						Mono.error(SubscriberAlreadyActiveException())

					SubscriberStatus.UNSUBSCRIBED ->
						subscriberRepository.save(
							existing.copy(
								fullName = request.fullName?.trim()?.takeIf { it.isNotEmpty() } ?: existing.fullName,
								status = SubscriberStatus.ACTIVE,
								unsubscribeToken = UUID.randomUUID().toString(),
								subscribedAt = Instant.now(),
								unsubscribedAt = null,
							),
						)
				}
			}
			.switchIfEmpty(
				Mono.defer {
					subscriberRepository.save(
						Subscriber(
							email = normalizedEmail,
							fullName = request.fullName?.trim()?.takeIf { it.isNotEmpty() },
							status = SubscriberStatus.ACTIVE,
							unsubscribeToken = UUID.randomUUID().toString(),
							subscribedAt = Instant.now(),
						),
					)
				},
			)
			.map(::toResponse)
	}

	fun listSubscribers(statusFilter: SubscriberStatus?): Mono<SubscriberListResponse> {
		val subscribersFlux = if (statusFilter != null) {
			subscriberRepository.findAllByStatus(statusFilter)
		} else {
			subscriberRepository.findAll()
		}

		return Mono.zip(
			subscribersFlux.map(::toResponse).collectList(),
			subscriberRepository.countByStatus(SubscriberStatus.ACTIVE).defaultIfEmpty(0L),
		) { subscribers, totalActive ->
			SubscriberListResponse(
				subscribers = subscribers,
				totalActive = totalActive.toInt(),
			)
		}
	}

	fun deleteSubscriber(id: String): Mono<Void> =
		subscriberRepository.deleteById(id)

	fun unsubscribeByToken(token: String): Mono<Boolean> =
		subscriberRepository.findByUnsubscribeToken(token.trim())
			.flatMap { subscriber ->
				if (subscriber.status == SubscriberStatus.UNSUBSCRIBED) {
					Mono.just(true)
				} else {
					subscriberRepository.save(
						subscriber.copy(
							status = SubscriberStatus.UNSUBSCRIBED,
							unsubscribedAt = Instant.now(),
						),
					).thenReturn(true)
				}
			}
			.defaultIfEmpty(false)

	internal fun getActiveEmailAddresses(): Flux<String> =
		subscriberRepository.findAllByStatus(SubscriberStatus.ACTIVE)
			.map { it.email }

	private fun toResponse(subscriber: Subscriber): SubscriberResponse =
		SubscriberResponse(
			id = requireNotNull(subscriber.id) { "Subscriber id must not be null" },
			email = subscriber.email,
			fullName = subscriber.fullName,
			status = subscriber.status,
			subscribedAt = subscriber.subscribedAt,
			unsubscribedAt = subscriber.unsubscribedAt,
		)
}
