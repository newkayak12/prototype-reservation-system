package com.reservation.user.self.port.input

import com.reservation.user.self.port.output.LoadGeneralUserByLoginIdAndEmail.LoadGeneralUserByLoginIdAndEmailInquiry

/**
 * 일반 사용자의 비밀번호 찾기를 요구합니다.
 * 내부적으로 새로운 임시 비밀번호를 발급 받고 이메일로 전송합니다.
 * 일련의 과정에 문제가 없으면 `true` 문제가 있으면 `false`를 리턴합니다.
 */
fun interface FindGeneralUserPasswordCommand {
    fun execute(command: FindGeneralUserPasswordCommandDto): Boolean

    data class FindGeneralUserPasswordCommandDto(
        val loginId: String,
        val email: String,
    ) {
        fun toInquiry(): LoadGeneralUserByLoginIdAndEmailInquiry =
            LoadGeneralUserByLoginIdAndEmailInquiry(loginId, email)
    }
}
