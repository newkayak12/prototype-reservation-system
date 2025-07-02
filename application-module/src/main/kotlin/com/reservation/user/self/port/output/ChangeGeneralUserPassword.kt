package com.reservation.user.self.port.output

import java.time.LocalDateTime

fun interface ChangeGeneralUserPassword {
    fun command(inquiry: ChangeGeneralUserPasswordInquiry): Boolean

    data class ChangeGeneralUserPasswordInquiry(
        val id: String,
        val encodedPassword: String,
        val oldEncodedPassword: String,
        val changedDateTime: LocalDateTime,
    )
}
