package com.umt.core.user

import com.umt.api.generated.UserApi
import com.umt.api.generated.model.CreateUserRequest
import com.umt.api.generated.model.UserResponse
import com.umt.core.user.UserMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userService: UserService,
    private val userMapper: UserMapper
) : UserApi {
    override fun getAllUsers(): ResponseEntity<List<UserResponse>> =
        ResponseEntity.ok(userService.getAllUsers().map(userMapper::toResponse))

    override fun createUser(createUserRequest: CreateUserRequest): ResponseEntity<UserResponse> {
        val result = userService.findOrCreateUser(userMapper.toEntity(createUserRequest))
        return ResponseEntity.status(result.toHttpStatus()).body(userMapper.toResponse(result.entity))
    }
}