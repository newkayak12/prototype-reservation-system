package com.reservation.resign.port.output

import com.reservation.resign.self.EncryptedAttributes
import com.reservation.user.shared.LoginId
import java.time.LocalDateTime

@FunctionalInterface
interface ResignTargetUser {
    fun command(inquiry: ResignInquiry): Boolean

    data class ResignInquiry(
        val id: String,
        val loginId: LoginId,
        val encryptedAttributes: EncryptedAttributes,
        val withdrawalDateTime: LocalDateTime,
    ) {
        val userLoginId: String
            get() = loginId.loginId
        val encryptedEmail: String
            get() = encryptedAttributes.encryptedEmail
        val encryptedNickname: String
            get() = encryptedAttributes.encryptedNickname
        val encryptedMobile: String
            get() = encryptedAttributes.encryptedMobile
        val encryptedRole: String
            get() = encryptedAttributes.encryptedRole
    }
}
