package com.umt.core.user

import dto.request.CreateUserRequest
import dto.response.UserResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class UserController(
    private val userService: UserService
) : UserApi {
    override fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        // *userService call here*
        return ResponseEntity.ok(
            listOf(
                UserResponse(UUID.randomUUID(), "johndoe", "johndoe@gmail.com")
            )
        )
    }

    override fun createUser(request: CreateUserRequest): ResponseEntity<UserResponse> {
        TODO("Not yet implemented")
    }
}