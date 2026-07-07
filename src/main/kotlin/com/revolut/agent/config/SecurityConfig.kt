package com.revolut.agent.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
	private val jwtAuthenticationWebFilter: JwtAuthenticationWebFilter,
) {

	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

	@Bean
	fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
		http
			.csrf { it.disable() }
			.httpBasic { it.disable() }
			.formLogin { it.disable() }
			.logout { it.disable() }
			.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
			.authorizeExchange { exchanges ->
				exchanges
					.pathMatchers("/api/auth/login").permitAll()
					.pathMatchers("/actuator/health/**").permitAll()
					.anyExchange().authenticated()
			}
			.exceptionHandling { exceptions ->
				exceptions.authenticationEntryPoint { exchange, _ ->
					val response = exchange.response
					response.statusCode = HttpStatus.UNAUTHORIZED
					response.headers.contentType = MediaType.APPLICATION_JSON
					val body: DataBuffer = response.bufferFactory().wrap(
						"""{"error":"Unauthorized","status":401}""".toByteArray(),
					)
					response.writeWith(Mono.just(body))
				}
				exceptions.accessDeniedHandler { exchange, _ ->
					val response = exchange.response
					response.statusCode = HttpStatus.FORBIDDEN
					response.headers.contentType = MediaType.APPLICATION_JSON
					val body: DataBuffer = response.bufferFactory().wrap(
						"""{"error":"Forbidden","status":403}""".toByteArray(),
					)
					response.writeWith(Mono.just(body))
				}
			}
			.addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
			.build()
}
