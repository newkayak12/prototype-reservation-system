package com.reservation.user.self.port.output

interface SendFindGeneralUserPasswordAsEmail {
    fun execute(form: FindGeneralUserPasswordEmailForm)

    data class FindGeneralUserPasswordEmailForm(
        val email: String,
        val password: String,
    )
}
