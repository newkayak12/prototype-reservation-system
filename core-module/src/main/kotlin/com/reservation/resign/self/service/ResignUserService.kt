package com.reservation.resign.self.service

import com.reservation.resign.self.EncryptedAttributes
import com.reservation.resign.self.ResignedUser
import com.reservation.user.policy.availables.UserResignable
import com.reservation.utilities.encrypt.bidirectional.BidirectionalEncryptUtility

class ResignUserService(
    private val bidirectionalEncryptUtility: BidirectionalEncryptUtility,
) {
    fun <T : UserResignable> resign(target: T): ResignedUser {
        val encryptedAttributes =
            EncryptedAttributes(
                bidirectionalEncryptUtility.encrypt(target.userEmail),
                bidirectionalEncryptUtility.encrypt(target.userNickname),
                bidirectionalEncryptUtility.encrypt(target.userMobile),
                bidirectionalEncryptUtility.encrypt(target.userRole.name),
            )

        return target.resign(encryptedAttributes)
    }
}
