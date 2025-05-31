package com.reservation.user.policy.availables

import com.reservation.enumeration.Role
import com.reservation.user.widthdrawal.EncryptedAttributes
import com.reservation.user.widthdrawal.WithdrawalUser

interface UserWithdrawable {
    val userEmail: String
    val userMobile: String
    val userNickname: String
    val userRole: Role

    fun withdraw(encryptedAttributes: EncryptedAttributes): WithdrawalUser
}
