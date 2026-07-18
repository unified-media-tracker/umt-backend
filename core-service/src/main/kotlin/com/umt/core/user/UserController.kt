package com.umt.core.user

import com.umt.core.user.dto.UserMapper
import com.umt.core.user.dto.request.CreateUserRequest
import com.umt.core.user.dto.response.UserResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userService: UserService,
    private val userMapper: UserMapper
) : UserApi {
    override fun getAllUsers(): ResponseEntity<List<UserResponse>> =
        ResponseEntity.ok(userService.getAllUsers().map(userMapper::toResponse))

    override fun createUser(request: CreateUserRequest): ResponseEntity<UserResponse> {
        val result = userService.findOrCreateUser(userMapper.toEntity(request))
        return ResponseEntity.status(result.toHttpStatus()).body(userMapper.toResponse(result.entity))
    }
}