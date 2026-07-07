package com.revolut.agent.config

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(-2)
class RequestLoggingWebFilter : WebFilter {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
		val request = exchange.request
		val method = request.method.name()
		val path = request.uri.path
		val query = request.uri.query?.let { "?$it" }.orEmpty()
		val start = System.currentTimeMillis()

		return chain.filter(exchange)
			.doOnSuccess {
				val status = exchange.response.statusCode?.value() ?: 0
				val duration = System.currentTimeMillis() - start
				log.info("{} {}{} -> {} ({} ms)", method, path, query, status, duration)
			}
			.doOnError { error ->
				val duration = System.currentTimeMillis() - start
				log.warn("{} {}{} -> ERROR ({} ms): {}", method, path, query, duration, error.message)
			}
	}
}
