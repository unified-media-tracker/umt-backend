package com.umt.core.user.dto.request

data class CreateUserRequest(
    val keycloakId: String,
    val username: String,
    val email: String,
    val avatarUrl: String? = null,
)