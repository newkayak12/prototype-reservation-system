package com.reservation.user.service

import com.reservation.encrypt.aes.AESUtility
import com.reservation.user.policy.UserWithdrawable
import com.reservation.user.widthdrawal.EncryptedAttributes
import com.reservation.user.widthdrawal.WithdrawalUser

class UserWithdrawService(
    val aesUtility: AESUtility,
) {
    fun <T : UserWithdrawable> withdraw(target: T): WithdrawalUser {
        val encryptedAttributes: EncryptedAttributes =
            EncryptedAttributes(
                aesUtility.encrypt(target.email()),
                aesUtility.encrypt(target.nickname()),
                aesUtility.encrypt(target.mobile()),
                aesUtility.encrypt(target.role().name),
            )

        return target.withdraw(encryptedAttributes)
    }
}
