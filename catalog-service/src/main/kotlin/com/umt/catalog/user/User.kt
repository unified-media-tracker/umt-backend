package com.umt.catalog.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.util.UUID
import kotlin.time.Instant

enum class PlanTier {
    FREE, PREMIUM
}

@Entity
@Table(name = User.TABLE_NAME)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @field:Column(name = KEYCLOAK_ID_COLUMN, nullable = false, unique = true, length = 255)
    var keycloakId: String,

    @field:Column(name = USERNAME_COLUMN, nullable = false, unique = true, length = 50)
    var username: String,

    @field:Column(name = EMAIL_COLUMN, nullable = false, unique = true, length = 255)
    var email: String,

    @field:Column(name = AVATAR_URL_COLUMN)
    var avatarUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = PLAN_TIER_COLUMN, nullable = false)
    var planTier: PlanTier = PlanTier.FREE
) {
    @CreationTimestamp
    @field:Column(name = CREATED_AT_COLUMN, nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @field:Column(name = UPDATED_AT_COLUMN, nullable = false)
    var updatedAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return keycloakId == other.keycloakId
    }

    override fun hashCode(): Int {
        return keycloakId.hashCode()
    }

    companion object {
        const val TABLE_NAME = "\"user\""
        const val ID_COLUMN = "id"
        const val KEYCLOAK_ID_COLUMN = "keycloak_id"
        const val USERNAME_COLUMN = "username"
        const val EMAIL_COLUMN = "email"
        const val AVATAR_URL_COLUMN = "avatar_url"
        const val PLAN_TIER_COLUMN = "plan_tier"
        const val CREATED_AT_COLUMN = "created_at"
        const val UPDATED_AT_COLUMN = "updated_at"
    }
}