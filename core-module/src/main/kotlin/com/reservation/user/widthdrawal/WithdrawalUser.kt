package com.reservation.user.widthdrawal

import com.reservation.shared.user.LoginId
import java.time.LocalDateTime

class WithdrawalUser (
    private val id: String,
    private val loginId: LoginId,
    private val encryptedAttributes: EncryptedAttributes,
    private val withdrawalDateTime: LocalDateTime
)
