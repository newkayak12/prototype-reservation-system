package com.reservation.user.self.port.input

@FunctionalInterface
interface CreateGeneralUserCommand {
    fun execute(command: CreateGeneralUserCommandDto): Boolean

    data class CreateGeneralUserCommandDto(
        val loginId: String,
        val password: String,
        val email: String,
        val mobile: String,
        val nickname: String,
    )
}
