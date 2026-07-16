package com.revolut.agent.controller.subscriber
import com.revolut.agent.dto.subscriber.CreateSubscriberRequest
import com.revolut.agent.dto.subscriber.SubscriberListResponse
import com.revolut.agent.dto.subscriber.SubscriberResponse
import com.revolut.agent.model.subscriber.SubscriberStatus
import com.revolut.agent.service.subscriber.SubscriberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/subscribers")
class SubscriberController(
	private val subscriberService: SubscriberService,
) {

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun createSubscriber(@Valid @RequestBody request: CreateSubscriberRequest): Mono<SubscriberResponse> =
		subscriberService.createSubscriber(request)

	@GetMapping
	fun listSubscribers(
		@RequestParam(required = false) status: SubscriberStatus?,
	): Mono<SubscriberListResponse> =
		subscriberService.listSubscribers(status)

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun deleteSubscriber(@PathVariable id: String): Mono<Void> =
		subscriberService.deleteSubscriber(id)
}
