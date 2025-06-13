package com.reservation.user.policy.availables

import com.reservation.enumeration.Role
import com.reservation.resign.self.EncryptedAttributes
import com.reservation.resign.self.ResignedUser

interface UserResignable {
    val userEmail: String
    val userMobile: String
    val userNickname: String
    val userRole: Role

    fun resign(encryptedAttributes: EncryptedAttributes): ResignedUser
}
