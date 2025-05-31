package com.reservation.user.service

import com.reservation.user.policy.UserWithdrawable
import com.reservation.user.widthdrawal.EncryptedAttributes
import com.reservation.user.widthdrawal.WithdrawalUser
import com.reservation.utilities.encrypt.bidirectional.BidirectionalEncryptUtility

class UserWithdrawService(
    val bidirectionalEncryptUtility: BidirectionalEncryptUtility,
) {
    fun <T : UserWithdrawable> withdraw(target: T): WithdrawalUser {
        val encryptedAttributes: EncryptedAttributes =
            EncryptedAttributes(
                bidirectionalEncryptUtility.encrypt(target.email()),
                bidirectionalEncryptUtility.encrypt(target.nickname()),
                bidirectionalEncryptUtility.encrypt(target.mobile()),
                bidirectionalEncryptUtility.encrypt(target.role().name),
            )

        return target.withdraw(encryptedAttributes)
    }
}
