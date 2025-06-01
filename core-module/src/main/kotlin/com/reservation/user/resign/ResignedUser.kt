package com.reservation.user.resign

import com.reservation.shared.user.LoginId
import java.time.LocalDateTime

class ResignedUser(
    private val id: String,
    private val loginId: LoginId,
    private val encryptedAttributes: EncryptedAttributes,
    private val withdrawalDateTime: LocalDateTime,
)
