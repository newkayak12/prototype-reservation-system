package com.reservation.user.self.port.input

import com.reservation.enumeration.Role

/**
 * 닉네임 변경을 요청합니다.
 * 닉네임 변경에 성공하면 `true` 실패하면 `false`를 반환합니다.
 */
fun interface ChangeGeneralUserNicknameCommand {
    fun execute(command: ChangeGeneralUserNicknameCommandDto): Boolean

    data class ChangeGeneralUserNicknameCommandDto(
        val id: String,
        val nickname: String,
        val role: Role = Role.USER,
    )
}
