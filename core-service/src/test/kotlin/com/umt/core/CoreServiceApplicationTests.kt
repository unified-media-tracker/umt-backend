package com.umt.core

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Boots the whole application against throwaway PostgreSQL and RabbitMQ containers.
 *
 * Deliberately not H2: V1__init.sql uses native `CREATE TYPE ... AS ENUM` and
 * `gen_random_uuid()`, and the entities map those enums with @JdbcTypeCode(NAMED_ENUM). On H2
 * the migration would have to be rewritten, which would mean testing a schema we never ship.
 * Real Postgres keeps the test honest.
 *
 * `ddl-auto: validate` makes this test do this — Hibernate compares every entity mapping
 * against the schema Flyway just built, so a column renamed in one place and not the other
 * fails here instead of in production.
 *
 * `disabledWithoutDocker` means a developer with no Docker running gets a skip rather than a
 * failure; CI always has Docker, so it always runs there.
 */
@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
        // no catalog calls happen during a context load, but the properties still have to bind
        "tmdb.api-key=test",
        "igdb.client-id=test",
        "igdb.client-secret=test",
    ]
)
@Testcontainers(disabledWithoutDocker = true)
class CoreServiceApplicationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @ServiceConnection
        @JvmStatic
        val rabbitmq = RabbitMQContainer("rabbitmq:3.13-alpine")
    }

    /**
     * Without this, Boot builds a JwtDecoder from `issuer-uri` and fetches Keycloak's OIDC
     * discovery document during startup, making the test depend on a running Keycloak. Role
     * mapping itself is covered by KeycloakJwtAuthenticationConverterTest.
     */
    @TestConfiguration
    class NoRemoteKeycloak {
        @Bean
        fun jwtDecoder(): JwtDecoder = JwtDecoder {
            throw UnsupportedOperationException("no token decoding in the context test")
        }
    }

    @Test
    fun `the application context loads and the Flyway schema matches every JPA entity`() {
        // The assertion is startup itself: Flyway migrates, then Hibernate validates every
        // @Entity against the result. Any drift fails before this body is reached.
    }
}
