package com.umt.core.user

class UserServiceImpl(
    private val userRepository: UserRepository
): UserService {
    override fun getAllUsers(): List<User> =
        userRepository.findAll()

    override fun saveUser(user: User): User =
        userRepository.save(user)

}