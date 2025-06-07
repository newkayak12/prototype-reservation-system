package com.reservation.user.self.port.output

interface ChangeGeneralUserNickname {
    fun changeGeneralUserNickname(inquiry: ChangeGeneralUserNicknameDto): Boolean

    data class ChangeGeneralUserNicknameDto(
        val id: String,
        val nickname: String,
    )
}
