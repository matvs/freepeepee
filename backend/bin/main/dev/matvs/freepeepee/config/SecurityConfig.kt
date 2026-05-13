package dev.matvs.freepeepee.config

import dev.matvs.freepeepee.security.JwtAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val jwtFilter: JwtAuthFilter,
    @Value("\${freepeepee.cors.allowed-origins}") private val origins: String
) {

    /** BCrypt cost 12 - tune if hardware changes. */
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }                          // stateless API - no cookies
        .cors { it.configurationSource(corsSource()) }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
              .requestMatchers("/actuator/health/**").permitAll()
              .anyRequest().authenticated()
        }
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        .httpBasic(AbstractHttpConfigurer<*, *>::disable)
        .formLogin(AbstractHttpConfigurer<*, *>::disable)
        .headers {
            it.frameOptions { fo -> fo.deny() }
              .contentSecurityPolicy { csp -> csp.policyDirectives("default-src 'self'") }
        }
        .build()

    private fun corsSource() = UrlBasedCorsConfigurationSource().apply {
        registerCorsConfiguration("/api/**", CorsConfiguration().apply {
            allowedOrigins = origins.split(",").map(String::trim)
            allowedMethods = listOf("GET","POST","PUT","DELETE","OPTIONS")
            allowedHeaders = listOf("Authorization","Content-Type","X-Request-Id")
            allowCredentials = false
            maxAge = 3600
        })
    }
}
