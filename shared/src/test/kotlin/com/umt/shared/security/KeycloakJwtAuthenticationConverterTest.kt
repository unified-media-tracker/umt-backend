package com.umt.shared.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Keycloak puts realm roles under realm_access.roles, which Spring Security knows nothing about
 * — without this converter, every authenticated user ends up with zero authorities, and every
 * hasRole() check silently denies. The malformed-claim cases matter because the claim is
 * attacker-adjacent input: it must degrade to "no authorities", never blow up the filter chain.
 */
class KeycloakJwtAuthenticationConverterTest {

    private val converter = KeycloakJwtAuthenticationConverter()

    private fun jwt(claims: Map<String, Any>): Jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("00000000-0000-0000-0000-000000000001")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .also { builder -> claims.forEach { (k, v) -> builder.claim(k, v) } }
            .build()

    /**
     * Only the ROLE_ authorities are this converter's responsibility. Spring Security also
     * contributes its own — 7.x adds FACTOR_BEARER, 6.x does not — and asserting on those would
     * tie the test to a framework version rather than to our mapping.
     */
    private fun authoritiesOf(claims: Map<String, Any>): Set<String> =
        checkNotNull(converter.convert(jwt(claims))) { "converter returned no token" }
            .authorities
            .map { it.authority!! }
            .filter { it.startsWith("ROLE_") }
            .toSet()

    @Test
    fun `maps realm roles to uppercase ROLE_ authorities`() {
        val authorities = authoritiesOf(
            mapOf("realm_access" to mapOf("roles" to listOf("admin", "user")))
        )

        assertEquals(setOf("ROLE_ADMIN", "ROLE_USER"), authorities)
    }

    @Test
    fun `uppercases roles that are already uppercase or mixed case`() {
        val authorities = authoritiesOf(
            mapOf("realm_access" to mapOf("roles" to listOf("ADMIN", "Moderator")))
        )

        assertEquals(setOf("ROLE_ADMIN", "ROLE_MODERATOR"), authorities)
    }

    @Test
    fun `grants no authorities when the realm_access claim is missing`() {
        assertTrue(authoritiesOf(mapOf("scope" to "openid")).isEmpty())
    }

    @Test
    fun `grants no authorities when realm_access has no roles key`() {
        assertTrue(authoritiesOf(mapOf("realm_access" to mapOf("other" to "value"))).isEmpty())
    }

    @Test
    fun `grants no authorities when the roles list is empty`() {
        assertTrue(
            authoritiesOf(mapOf("realm_access" to mapOf("roles" to emptyList<String>()))).isEmpty()
        )
    }

    @Test
    fun `degrades safely when roles is not a list`() {
        assertTrue(
            authoritiesOf(mapOf("realm_access" to mapOf("roles" to "admin"))).isEmpty()
        )
    }
}
