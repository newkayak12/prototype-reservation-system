package com.reservation.user.service

import com.reservation.user.policy.availables.UserResignable
import com.reservation.user.resign.EncryptedAttributes
import com.reservation.user.resign.ResignedUser
import com.reservation.utilities.encrypt.bidirectional.BidirectionalEncryptUtility

class ResignUserService(
    private val bidirectionalEncryptUtility: BidirectionalEncryptUtility,
) {
    fun <T : UserResignable> withdraw(target: T): ResignedUser {
        val encryptedAttributes: EncryptedAttributes =
            EncryptedAttributes(
                bidirectionalEncryptUtility.encrypt(target.userEmail),
                bidirectionalEncryptUtility.encrypt(target.userNickname),
                bidirectionalEncryptUtility.encrypt(target.userMobile),
                bidirectionalEncryptUtility.encrypt(target.userRole.name),
            )

        return target.resign(encryptedAttributes)
    }
}
