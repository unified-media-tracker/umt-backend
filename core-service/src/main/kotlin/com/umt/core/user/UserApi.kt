package com.umt.core.user

import com.umt.core.user.dto.request.CreateUserRequest
import com.umt.core.user.dto.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(name = "User", description = "Operations related to users")
@RequestMapping("/api/core/user")
interface UserApi {

    @Operation(summary = "Get all users")
    @GetMapping("/all", produces = ["application/json"])
    fun getAllUsers(): ResponseEntity<List<UserResponse>>

    @Operation(summary = "Create a new user")
    @PostMapping("/create", produces = ["application/json"], consumes = ["application/json"])
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse>
}