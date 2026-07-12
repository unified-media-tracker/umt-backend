package com.umt.core.user

import org.springframework.stereotype.Service

@Service
interface UserService {

    fun getAllUsers(): List<User>

    fun saveUser(user: User): User

}