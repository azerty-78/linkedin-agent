package com.revolut.agent.controller.dashboard

import com.revolut.agent.dto.dashboard.DashboardOverviewResponse
import com.revolut.agent.service.dashboard.DashboardOverviewService
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
	private val dashboardOverviewService: DashboardOverviewService,
) {

	@GetMapping("/overview")
	fun overview(): Mono<ResponseEntity<DashboardOverviewResponse>> =
		dashboardOverviewService.getOverview()
			.map { body ->
				ResponseEntity.ok()
					.cacheControl(CacheControl.noStore())
					.body(body)
			}
}
