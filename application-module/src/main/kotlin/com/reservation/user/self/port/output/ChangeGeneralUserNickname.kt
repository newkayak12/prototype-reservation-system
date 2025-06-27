package com.reservation.user.self.port.output

@FunctionalInterface
interface ChangeGeneralUserNickname {
    fun command(inquiry: ChangeGeneralUserNicknameDto): Boolean

    data class ChangeGeneralUserNicknameDto(
        val id: String,
        val nickname: String,
    )
}
