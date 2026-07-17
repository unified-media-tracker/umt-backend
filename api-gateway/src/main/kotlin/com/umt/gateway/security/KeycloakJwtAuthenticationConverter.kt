package com.umt.gateway.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter

class KeycloakJwtAuthenticationConverter : JwtAuthenticationConverter() {
    init {
        setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
            val realmAccess = jwt.getClaimAsMap("realm_access") ?: emptyMap()

            @Suppress("UNCHECKED_CAST")
            val roles = realmAccess["roles"] as? List<String> ?: emptyList()
            roles.map { SimpleGrantedAuthority("ROLE_${it.uppercase()}") } as Collection<GrantedAuthority>
        }
    }
}