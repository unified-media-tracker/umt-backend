package com.umt.core.common.config

import com.umt.shared.security.KeycloakJwtAuthenticationConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // temporary endpoint for testing. should be '/api/core/admin/users' in future
                    .requestMatchers(HttpMethod.GET, "/api/core/user/all").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(KeycloakJwtAuthenticationConverter()) } }

        return http.build()
    }
}