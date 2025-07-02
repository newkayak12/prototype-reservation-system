package com.reservation.user.resign.service

import com.reservation.user.policy.availables.UserResignable
import com.reservation.user.resign.EncryptedAttributes
import com.reservation.user.resign.ResignedUser
import com.reservation.utilities.encrypt.bidirectional.BidirectionalEncryptUtility

/**
 * 특정 사용자와 관련 없이 모든 계정에 대한 탈퇴 서비스를 다룹니다.
 */
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
