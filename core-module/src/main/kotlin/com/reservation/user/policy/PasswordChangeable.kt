package com.reservation.user.policy

import com.reservation.shared.user.Password

interface PasswordChangeable {
    fun changePassword(password: Password)

    fun password(): Password

    fun changePassword(encodedNewPassword: String) {
        changePassword(password().changePassword(encodedNewPassword))
    }
}
