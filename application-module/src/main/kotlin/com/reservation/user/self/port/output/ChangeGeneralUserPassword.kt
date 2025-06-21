package com.reservation.user.self.port.output

import java.time.LocalDateTime

@FunctionalInterface
interface ChangeGeneralUserPassword {
    fun changeGeneralUserPassword(inquiry: ChangeGeneralUserPasswordInquiry): Boolean

    data class ChangeGeneralUserPasswordInquiry(
        val id: String,
        val encodedPassword: String,
        val oldEncodedPassword: String,
        val changedDateTime: LocalDateTime,
    )
}
