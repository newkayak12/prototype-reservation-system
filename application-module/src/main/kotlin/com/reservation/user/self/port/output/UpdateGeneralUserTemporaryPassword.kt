package com.reservation.user.self.port.output

import java.time.LocalDateTime

@FunctionalInterface
interface UpdateGeneralUserTemporaryPassword {
    fun command(inquiry: UpdateGeneralUserPasswordInquiry): Boolean

    data class UpdateGeneralUserPasswordInquiry(
        val id: String,
        val newEncodedPassword: String,
        val oldEncodedPassword: String,
        val changedDateTime: LocalDateTime,
        val isNeedToChangePassword: Boolean = true,
    )
}
