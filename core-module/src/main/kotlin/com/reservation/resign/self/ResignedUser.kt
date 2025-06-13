package com.reservation.resign.self

import com.reservation.shared.user.LoginId
import java.time.LocalDateTime

class ResignedUser(
    val id: String,
    val loginId: LoginId,
    val encryptedAttributes: EncryptedAttributes,
    val withdrawalDateTime: LocalDateTime,
)
