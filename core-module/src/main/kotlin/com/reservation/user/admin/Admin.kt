package com.reservation.user.admin

import com.reservation.shared.user.Password
import com.reservation.user.policy.PasswordChangeable
import com.reservation.user.policy.Withdrawable
import com.reservation.user.widthdrawal.WithdrawalUser

class Admin(
    private val id: String,
    private var password: Password
): Withdrawable, PasswordChangeable {

    override fun withdraw(): WithdrawalUser {
        TODO("Not yet implemented")
    }

    override fun password(): Password = password

    override fun changePassword(password: Password) {
        this.password = password
    }
}
