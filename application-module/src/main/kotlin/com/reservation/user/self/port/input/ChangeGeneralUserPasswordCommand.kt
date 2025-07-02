package com.reservation.user.self.port.input

/**
 * 일반 사용자의 비밀번호 변경을 요청합니다.
 * 비밀번호 변경에 성공하면 `true` 실패하면 `false`를 반환합니다.
 */
fun interface ChangeGeneralUserPasswordCommand {
    fun execute(command: ChangeGeneralUserPasswordCommandDto): Boolean

    data class ChangeGeneralUserPasswordCommandDto(
        val id: String,
        val encodedPassword: String,
    )
}
