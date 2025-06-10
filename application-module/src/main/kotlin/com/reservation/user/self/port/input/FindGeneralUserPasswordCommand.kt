package com.reservation.user.self.port.input

import com.reservation.user.self.port.output.LoadGeneralUserByLoginIdAndEmail.LoadGeneralUserByLoginIdAndEmailInquiry

interface FindGeneralUserPasswordCommand {
    fun execute(command: FindGeneralUserPasswordCommandDto): Boolean

    data class FindGeneralUserPasswordCommandDto(
        val loginId: String,
        val email: String,
    ) {
        fun toInquiry(): LoadGeneralUserByLoginIdAndEmailInquiry =
            LoadGeneralUserByLoginIdAndEmailInquiry(loginId, email)
    }
}
