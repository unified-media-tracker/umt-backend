package dto.request

data class CreateUserRequest(
    val username: String,
    val email: String,
    val imageUrl: String? = null,
)
