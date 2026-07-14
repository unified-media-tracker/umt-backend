package com.umt.core.user

interface UserService {
    fun getAllUsers(): List<User>

    fun saveUser(user: User): User
}