package com.revolut.agent.config

import com.revolut.agent.service.auth.JwtService
import com.revolut.agent.service.auth.UserSessionService
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
	private val userSessionService: UserSessionService,
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

		val jti = jwtService.extractJti(token)
		if (jti.isNullOrBlank()) {
			// Anciens tokens sans jti / session → rejetés
			return chain.filter(exchange)
		}

		return userSessionService.isSessionActive(jti)
			.flatMap { active ->
				if (!active) {
					chain.filter(exchange)
				} else {
					val email = jwtService.extractEmail(token)
					val authentication = UsernamePasswordAuthenticationToken(
						email,
						null,
						listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
					)
					RequestLogSupport.capturePrincipal(exchange, authentication)
					chain.filter(exchange)
						.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
				}
			}
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "
	}
}
