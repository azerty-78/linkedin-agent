package com.revolut.agent.config

import com.revolut.agent.service.JwtService
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationWebFilter(
	private val jwtService: JwtService,
) : WebFilter {

	override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
		val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
			?: return chain.filter(exchange)

		if (!authHeader.startsWith(BEARER_PREFIX)) {
			return chain.filter(exchange)
		}

		val token = authHeader.removePrefix(BEARER_PREFIX).trim()
		if (!jwtService.validateToken(token)) {
			return chain.filter(exchange)
		}

		val email = jwtService.extractEmail(token)
		val authentication = UsernamePasswordAuthenticationToken(
			email,
			null,
			listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
		)

		return chain.filter(exchange)
			.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "
	}
}
