package com.reservation.shared.user

import com.reservation.user.common.exceptions.UseSamePasswordAsBeforeException
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import java.time.LocalDateTime

data class Password(
    val encodedPassword: String,
    val oldEncodedPassword: String?,
    val changedDateTime: LocalDateTime?,
) {
    fun changePassword(rawNewPassword: String): Password {
        if (PasswordEncoderUtility.matches(rawNewPassword, encodedPassword)) {
            throw UseSamePasswordAsBeforeException()
        }

        return Password(
            PasswordEncoderUtility.encode(rawNewPassword),
            encodedPassword,
            LocalDateTime.now(),
        )
    }
}
