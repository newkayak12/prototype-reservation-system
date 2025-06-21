package com.reservation.user.self.port.input

@FunctionalInterface
interface ChangeGeneralUserPasswordCommand {
    fun execute(command: ChangeGeneralUserPasswordCommandDto): Boolean

    data class ChangeGeneralUserPasswordCommandDto(
        val id: String,
        val encodedPassword: String,
    )
}
