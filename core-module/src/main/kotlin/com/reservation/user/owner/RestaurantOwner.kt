package com.reservation.user.owner

import com.reservation.enumeration.Role
import com.reservation.enumeration.Role.RESTAURANT_OWNER
import com.reservation.user.common.exceptions.ResignWithoutIdException
import com.reservation.user.policy.formats.ServiceUser
import com.reservation.user.resign.EncryptedAttributes
import com.reservation.user.resign.ResignedUser
import com.reservation.user.shared.LoginId
import com.reservation.user.shared.Password
import com.reservation.user.shared.PersonalAttributes
import com.reservation.user.shared.UserAttribute
import java.time.LocalDateTime

class RestaurantOwner(
    private val id: String? = null,
    private val loginId: LoginId,
    private var password: Password,
    private var personalAttributes: PersonalAttributes,
    nickname: String,
) : ServiceUser {
    private var userAttributes: UserAttribute = UserAttribute(nickname, RESTAURANT_OWNER)

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

    override fun resign(encryptedAttributes: EncryptedAttributes): ResignedUser {
        if (id == null) {
            throw ResignWithoutIdException()
        }

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

    override fun personalAttributes(): PersonalAttributes = personalAttributes

    override fun userAttributes(): UserAttribute = userAttributes

    override fun changePersonalAttributes(personalAttributes: PersonalAttributes) {
        this.personalAttributes = personalAttributes
    }

    override fun changeUserNickname(userAttributes: UserAttribute) {
        this.userAttributes = userAttributes
    }
}
