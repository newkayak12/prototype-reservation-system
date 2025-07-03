package com.reservation.user.resign

import com.reservation.user.shared.vo.LoginId
import java.time.LocalDateTime

class ResignedUser(
    val id: String,
    val loginId: LoginId,
    val encryptedAttributes: EncryptedAttributes,
    val withdrawalDateTime: LocalDateTime,
)
