package com.revolut.agent.config

import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.util.context.ContextView
import java.net.InetSocketAddress
import java.util.UUID

internal object RequestLogSupport {

	const val REQUEST_ID_ATTR = "requestLog.requestId"
	const val REQUEST_START_ATTR = "requestLog.startTimeMs"

	private const val REQUEST_ID_HEADER = "X-Request-Id"
	private const val CORRELATION_ID_HEADER = "X-Correlation-Id"
	private val SENSITIVE_QUERY_KEYS = setOf("password", "token", "apikey", "api_key", "secret", "authorization")

	fun ensureRequestId(exchange: ServerWebExchange): String {
		val existing = exchange.getAttribute<String>(REQUEST_ID_ATTR)
		if (existing != null) {
			return existing
		}

		val requestId = exchange.request.headers.getFirst(REQUEST_ID_HEADER)
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?: exchange.request.headers.getFirst(CORRELATION_ID_HEADER)
				?.trim()
				?.takeIf { it.isNotEmpty() }
			?: UUID.randomUUID().toString()

		exchange.attributes[REQUEST_ID_ATTR] = requestId
		exchange.response.headers.set(REQUEST_ID_HEADER, requestId)
		return requestId
	}

	fun captureStart(exchange: ServerWebExchange) {
		exchange.attributes[REQUEST_START_ATTR] = System.currentTimeMillis()
	}

	fun durationMs(exchange: ServerWebExchange): Long {
		val start = exchange.getAttribute<Long>(REQUEST_START_ATTR) ?: return 0L
		return System.currentTimeMillis() - start
	}

	fun buildRequestMetadata(exchange: ServerWebExchange, requestId: String): Map<String, String> {
		val request = exchange.request
		val correlationId = request.headers.getFirst(CORRELATION_ID_HEADER)?.trim().orEmpty()

		return linkedMapOf(
			"requestId" to requestId,
			"correlationId" to correlationId.ifBlank { requestId },
			"method" to request.method.name(),
			"path" to request.uri.path,
			"query" to sanitizeQuery(request.uri.query),
			"clientIp" to resolveClientIp(request),
			"userAgent" to truncate(request.headers.getFirst(HttpHeaders.USER_AGENT), 160),
			"contentType" to (request.headers.getFirst(HttpHeaders.CONTENT_TYPE) ?: "-"),
			"accept" to truncate(request.headers.getFirst(HttpHeaders.ACCEPT), 120),
			"contentLength" to (request.headers.contentLength.takeIf { it >= 0 }?.toString() ?: "-"),
			"referer" to truncate(request.headers.getFirst(HttpHeaders.REFERER), 120),
			"host" to (request.headers.getFirst(HttpHeaders.HOST) ?: "-"),
		)
	}

	fun buildResponseMetadata(exchange: ServerWebExchange): Map<String, String> {
		val status = exchange.response.statusCode?.value() ?: 0
		val responseContentType = exchange.response.headers.getFirst(HttpHeaders.CONTENT_TYPE) ?: "-"
		val outcome = when {
			status == 0 -> "unknown"
			status < 400 -> "success"
			status < 500 -> "client_error"
			else -> "server_error"
		}

		return linkedMapOf(
			"status" to status.toString(),
			"outcome" to outcome,
			"responseContentType" to responseContentType,
			"durationMs" to durationMs(exchange).toString(),
		)
	}

	fun resolvePrincipal(contextView: ContextView): Map<String, String> {
		if (!contextView.hasKey(SecurityContext::class.java)) {
			return anonymousPrincipal()
		}

		@Suppress("UNCHECKED_CAST")
		val securityContextMono = contextView.get(SecurityContext::class.java) as Mono<SecurityContext>
		val authentication: Authentication? = securityContextMono
			.flatMap { context -> Mono.justOrEmpty(context.authentication) }
			.onErrorResume { _: Throwable -> Mono.empty<Authentication>() }
			.blockOptional()
			.orElse(null)

		if (authentication == null || !authentication.isAuthenticated) {
			return anonymousPrincipal()
		}

		val roles = authentication.authorities
			.joinToString(",") { authority -> (authority.authority ?: "").removePrefix("ROLE_") }
			.ifBlank { "-" }

		return mapOf(
			"principal" to (authentication.name ?: "anonymous"),
			"roles" to roles,
			"authenticated" to "true",
		)
	}

	fun format(metadata: Map<String, String>): String =
		metadata.entries.joinToString(separator = " | ") { (key, value) ->
			"$key=$value"
		}

	private fun resolveClientIp(request: ServerHttpRequest): String {
		val forwarded = request.headers.getFirst("X-Forwarded-For")
			?.split(",")
			?.firstOrNull()
			?.trim()
		if (!forwarded.isNullOrBlank()) {
			return forwarded
		}

		val realIp = request.headers.getFirst("X-Real-IP")?.trim()
		if (!realIp.isNullOrBlank()) {
			return realIp
		}

		return (request.remoteAddress as? InetSocketAddress)
			?.address
			?.hostAddress
			?: "-"
	}

	private fun sanitizeQuery(query: String?): String {
		if (query.isNullOrBlank()) {
			return "-"
		}

		return query.split("&")
			.joinToString("&") { part ->
				val key = part.substringBefore("=").lowercase()
				if (SENSITIVE_QUERY_KEYS.any { key.contains(it) }) {
					"${part.substringBefore("=")}=***"
				} else {
					part
				}
			}
	}

	private fun anonymousPrincipal(): Map<String, String> = mapOf(
		"principal" to "anonymous",
		"roles" to "-",
		"authenticated" to "false",
	)

	private fun truncate(value: String?, maxLength: Int): String {
		if (value.isNullOrBlank()) {
			return "-"
		}
		return if (value.length <= maxLength) value else value.take(maxLength) + "..."
	}
}
