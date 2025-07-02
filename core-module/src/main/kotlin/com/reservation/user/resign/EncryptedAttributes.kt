package com.reservation.user.resign

data class EncryptedAttributes(
    val encryptedEmail: String,
    val encryptedNickname: String,
    val encryptedMobile: String,
    val encryptedRole: String,
)
