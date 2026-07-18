package com.umt.core.user.dto

import com.umt.core.user.User
import com.umt.core.user.dto.request.CreateUserRequest
import com.umt.core.user.dto.response.UserResponse
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "avatarUrl")
    @Mapping(target = "planTier", constant = "FREE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    fun toEntity(request: CreateUserRequest): User

    fun toResponse(user: User): UserResponse
}