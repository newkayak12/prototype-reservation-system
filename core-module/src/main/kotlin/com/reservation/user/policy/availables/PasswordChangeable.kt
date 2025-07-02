package com.reservation.user.policy.availables

import com.reservation.user.shared.Password

interface PasswordChangeable {
    fun changePassword(password: Password)

    val userPasswordSet: Password

    fun changePassword(
        rawNewPassword: String,
        isNeedToChangePassword: Boolean,
    ) {
        changePassword(userPasswordSet.changePassword(rawNewPassword, isNeedToChangePassword))
    }
}
