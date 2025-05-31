package com.reservation.user.service

import com.reservation.user.policy.availables.UserWithdrawable
import com.reservation.user.widthdrawal.EncryptedAttributes
import com.reservation.user.widthdrawal.WithdrawalUser
import com.reservation.utilities.encrypt.bidirectional.BidirectionalEncryptUtility

class WithdrawUserService(
    private val bidirectionalEncryptUtility: BidirectionalEncryptUtility,
) {
    fun <T : UserWithdrawable> withdraw(target: T): WithdrawalUser {
        val encryptedAttributes: EncryptedAttributes =
            EncryptedAttributes(
                bidirectionalEncryptUtility.encrypt(target.userEmail),
                bidirectionalEncryptUtility.encrypt(target.userNickname),
                bidirectionalEncryptUtility.encrypt(target.userMobile),
                bidirectionalEncryptUtility.encrypt(target.userRole.name),
            )

        return target.withdraw(encryptedAttributes)
    }
}
