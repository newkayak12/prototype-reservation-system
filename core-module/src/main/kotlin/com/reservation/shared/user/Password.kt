package com.reservation.shared.user

import java.time.LocalDateTime

data class Password(
    val encodedPassword: String,
    val oldEncodedPassword: String?,
    val changedDateTime: LocalDateTime?,
) {
    fun changePassword(encodedNewPassword: String): Password {
        return Password(
            encodedNewPassword,
            encodedPassword,
            LocalDateTime.now(),
        )
    }
}
