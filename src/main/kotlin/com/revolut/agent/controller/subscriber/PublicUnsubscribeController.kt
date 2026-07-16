package com.revolut.agent.controller.subscriber
import com.revolut.agent.service.subscriber.SubscriberService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import reactor.core.publisher.Mono

@Controller
@RequestMapping("/api/public")
class PublicUnsubscribeController(
	private val subscriberService: SubscriberService,
) {

	@GetMapping("/unsubscribe/{token}")
	fun unsubscribe(@PathVariable token: String, model: Model): Mono<String> =
		subscriberService.unsubscribeByToken(token)
			.map { success ->
				model.addAttribute("success", success)
				"unsubscribe-result"
			}
}
