package com.reservation.shared.user

import java.time.LocalDateTime

data class Password(
    val encodedPassword: String,
    val oldEncodedPassword: String?,
    val changedDateTime: LocalDateTime?,
    val isNeedToChangePassword: Boolean = false,
) {
    fun changePassword(
        encodedNewPassword: String,
        isNeedToChangePassword: Boolean = false,
    ): Password {
        return Password(
            encodedNewPassword,
            encodedPassword,
            LocalDateTime.now(),
            isNeedToChangePassword,
        )
    }
}
