package com.umt.core.user

import com.umt.core.common.result.FindOrCreateResult
import org.springframework.stereotype.Service

@Service
class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService {
    override fun getAllUsers(): List<User> =
        userRepository.findAll()

    override fun findOrCreateUser(user: User): FindOrCreateResult<User> {
        val existing = userRepository.findByKeycloakId(user.keycloakId)
        if (existing != null) return FindOrCreateResult(existing, created = false)
        return FindOrCreateResult(userRepository.save(user), created = true)
    }
}