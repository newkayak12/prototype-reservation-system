package com.reservation.user.resign

data class EncryptedAttributes(
    private val encryptedEmail: String,
    private val encryptedNickname: String,
    private val encryptedMobile: String,
    private val encryptedRole: String,
)
