package com.umt.core.user

import org.springframework.stereotype.Service

@Service
class UserServiceImpl(
    private val userRepository: UserRepository
): UserService {
    override fun getAllUsers(): List<User> =
        userRepository.findAll()

    override fun saveUser(user: User): User =
        userRepository.save(user)

}