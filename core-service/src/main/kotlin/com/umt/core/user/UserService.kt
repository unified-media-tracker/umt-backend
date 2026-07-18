package com.umt.core.user

import com.umt.core.common.result.FindOrCreateResult

interface UserService {
    fun getAllUsers(): List<User>

    fun findOrCreateUser(user: User): FindOrCreateResult<User>
}