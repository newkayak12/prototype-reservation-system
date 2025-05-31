package com.reservation.user.owner

import com.reservation.enumeration.Role
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.shared.user.UserAttribute
import com.reservation.user.common.exceptions.WithdrawalIdHaveNoIdException
import com.reservation.user.policy.formats.ServiceUser
import com.reservation.user.widthdrawal.EncryptedAttributes
import com.reservation.user.widthdrawal.WithdrawalUser
import java.time.LocalDateTime

class RestaurantOwner(
    private val id: String? = null,
    private val loginId: LoginId,
    private var password: Password,
    private var personalAttributes: PersonalAttributes,
    nickname: String,
) : ServiceUser {
    private var userAttributes: UserAttribute = UserAttribute(nickname, Role.RESTAURANT_OWNER)

    override val identifier: String?
        get() = id
    override val userEmail: String
        get() = personalAttributes.email
    override val userMobile: String
        get() = personalAttributes.mobile
    override val userNickname: String
        get() = userAttributes.nickname
    override val userRole: Role
        get() = userAttributes.role
    override val userPasswordSet: Password
        get() = password

    override fun withdraw(encryptedAttributes: EncryptedAttributes): WithdrawalUser {
        if (id == null) {
            throw WithdrawalIdHaveNoIdException()
        }

        return WithdrawalUser(
            id,
            loginId,
            encryptedAttributes,
            LocalDateTime.now(),
        )
    }

    override fun changePassword(password: Password) {
        this.password = password
    }

    override fun personalAttributes(): PersonalAttributes = personalAttributes

    override fun changePersonalAttributes(personalAttributes: PersonalAttributes) {
        this.personalAttributes = personalAttributes
    }
}
