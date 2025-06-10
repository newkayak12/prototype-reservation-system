package com.reservation.user.self.port.output

import java.time.LocalDateTime

interface UpdateGeneralUserTemporaryPassword {
    fun updateGeneralUserPassword(inquiry: UpdateGeneralUserPasswordInquiry): Boolean

    data class UpdateGeneralUserPasswordInquiry(
        val id: String,
        val newPassword: String,
        val oldEncodedPassword: String,
        val changedDateTime: LocalDateTime,
    )
}
