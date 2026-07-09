package com.revolut.agent.config

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType

@Component
@Order(-2)
class RequestLoggingWebFilter : WebFilter {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
		val requestId = RequestLogSupport.ensureRequestId(exchange)
		RequestLogSupport.captureStart(exchange)

		val requestMetadata = RequestLogSupport.buildRequestMetadata(exchange, requestId)
		log.debug("API request started | {}", RequestLogSupport.format(requestMetadata))

		return chain.filter(exchange)
			.transformDeferredContextual { mono, contextView ->
				mono
					.doOnError { error ->
						exchange.attributes[ERROR_ATTR] = error
					}
					.doFinally { signal ->
						val responseMetadata = RequestLogSupport.buildResponseMetadata(exchange)
						val principalMetadata = RequestLogSupport.resolvePrincipal(contextView)
						val metadata = requestMetadata + principalMetadata + responseMetadata

						when (signal) {
							SignalType.ON_COMPLETE -> {
								val status = responseMetadata["status"]?.toIntOrNull() ?: 0
								if (status >= 500) {
									log.error("API request completed | {}", RequestLogSupport.format(metadata))
								} else if (status >= 400) {
									log.warn("API request completed | {}", RequestLogSupport.format(metadata))
								} else {
									log.info("API request completed | {}", RequestLogSupport.format(metadata))
								}
							}

							SignalType.ON_ERROR -> {
								val throwable = exchange.getAttribute<Throwable>(ERROR_ATTR)
								val errorMetadata = linkedMapOf(
									"errorType" to (throwable?.javaClass?.simpleName ?: "unknown"),
									"errorMessage" to truncate(throwable?.message ?: "unknown error"),
								)
								log.error(
									"API request failed | {}",
									RequestLogSupport.format(metadata + errorMetadata),
									throwable,
								)
							}

							else -> Unit
						}
					}
			}
	}

	private fun truncate(value: String, maxLength: Int = 200): String =
		if (value.length <= maxLength) value else value.take(maxLength) + "..."

	companion object {
		private const val ERROR_ATTR = "requestLog.error"
	}
}
