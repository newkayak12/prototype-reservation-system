package com.reservation.resign.self

import com.reservation.user.shared.LoginId
import java.time.LocalDateTime

class ResignedUser(
    val id: String,
    val loginId: LoginId,
    val encryptedAttributes: EncryptedAttributes,
    val withdrawalDateTime: LocalDateTime,
)
