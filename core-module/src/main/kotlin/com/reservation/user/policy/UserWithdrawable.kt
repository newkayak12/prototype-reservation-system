package com.reservation.user.policy

import com.reservation.enumeration.Role
import com.reservation.user.widthdrawal.EncryptedAttributes
import com.reservation.user.widthdrawal.WithdrawalUser

interface UserWithdrawable {
    fun email(): String

    fun mobile(): String

    fun nickname(): String

    fun role(): Role

    fun withdraw(encryptedAttributes: EncryptedAttributes): WithdrawalUser
}
