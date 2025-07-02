package com.reservation.user.admin

import com.reservation.enumeration.Role
import com.reservation.user.policy.availables.PasswordChangeable
import com.reservation.user.policy.availables.UserResignable
import com.reservation.user.resign.EncryptedAttributes
import com.reservation.user.resign.ResignedUser
import com.reservation.user.shared.LoginId
import com.reservation.user.shared.Password
import java.time.LocalDateTime

class Admin(
    private val id: String,
    private val loginId: LoginId,
    private var password: Password,
    private val role: Role = Role.ROOT,
) : UserResignable, PasswordChangeable {
    override val userEmail: String
        get() = ""
    override val userMobile: String
        get() = ""
    override val userNickname: String
        get() = ""
    override val userRole: Role
        get() = role
    override val userPasswordSet: Password
        get() = password

    override fun resign(encryptedAttributes: EncryptedAttributes): ResignedUser {
        return ResignedUser(
            id,
            loginId,
            encryptedAttributes,
            LocalDateTime.now(),
        )
    }

    override fun changePassword(password: Password) {
        this.password = password
    }
}
