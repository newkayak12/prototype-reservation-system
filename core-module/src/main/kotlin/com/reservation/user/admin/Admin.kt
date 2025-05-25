package com.reservation.user.admin

import com.reservation.enumeration.Role
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.user.policy.PasswordChangeable
import com.reservation.user.policy.UserWithdrawable
import com.reservation.user.widthdrawal.EncryptedAttributes
import com.reservation.user.widthdrawal.WithdrawalUser
import java.time.LocalDateTime

class Admin(
    private val id: String,
    private val loginId: LoginId,
    private var password: Password,
    private val role: Role = Role.ROOT,
) : UserWithdrawable, PasswordChangeable {
    override fun email(): String = ""

    override fun mobile(): String = ""

    override fun nickname(): String = ""

    override fun role(): Role = role

    override fun withdraw(encryptedAttributes: EncryptedAttributes): WithdrawalUser {
        return WithdrawalUser(
            id,
            loginId,
            encryptedAttributes,
            LocalDateTime.now(),
        )
    }

    override fun password(): Password = password

    override fun changePassword(password: Password) {
        this.password = password
    }
}
