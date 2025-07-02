package com.reservation.user.policy.availables

import com.reservation.enumeration.Role
import com.reservation.user.resign.EncryptedAttributes
import com.reservation.user.resign.ResignedUser

interface UserResignable {
    val userEmail: String
    val userMobile: String
    val userNickname: String
    val userRole: Role

    fun resign(encryptedAttributes: EncryptedAttributes): ResignedUser
}
