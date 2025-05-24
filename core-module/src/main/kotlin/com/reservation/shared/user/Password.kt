package com.reservation.shared.user

import java.time.LocalDateTime

data class Password(
    val encodedPassword: String,
    private val oldEncodedPassword: String,
    private val changedDateTime: LocalDateTime,
) {
    fun changePassword(encodedNewPassword: String): Password {
        return Password(
            encodedNewPassword,
            encodedPassword,
            LocalDateTime.now(),
        )
    }
}
