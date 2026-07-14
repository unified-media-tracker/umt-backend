package com.umt.core.user

import dto.request.CreateUserRequest
import dto.response.UserResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@RequestMapping("/api/core/user")
interface UserApi {

    @GetMapping("/all", produces = ["application/json"])
    fun getAllUsers(): ResponseEntity<List<UserResponse>>

    @PostMapping("/create", produces = ["application/json"], consumes = ["application/json"])
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse>

}