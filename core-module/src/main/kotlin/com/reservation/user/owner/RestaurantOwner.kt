package com.reservation.user.owner

import com.reservation.enumeration.Role
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.shared.user.UserAttribute
import com.reservation.user.policy.PasswordChangeable
import com.reservation.user.policy.PersonalAttributesChangeable
import com.reservation.user.policy.UserWithdrawable
import com.reservation.user.widthdrawal.EncryptedAttributes
import com.reservation.user.widthdrawal.WithdrawalUser
import java.time.LocalDateTime

class RestaurantOwner(
    private val id: String,
    private val loginId: LoginId,
    private var password: Password,
    private var personalAttributes: PersonalAttributes,
    nickname: String,
) : UserWithdrawable, PasswordChangeable, PersonalAttributesChangeable {
    private var userAttributes: UserAttribute = UserAttribute(nickname, Role.RESTAURANT_OWNER)

    override fun email(): String = personalAttributes.email

    override fun mobile(): String = personalAttributes.mobile

    override fun nickname(): String = userAttributes.nickname

    override fun role(): Role = userAttributes.role

    override fun withdraw(encryptedAttributes: EncryptedAttributes): WithdrawalUser {
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

    override fun password(): Password = password

    override fun personalAttributes(): PersonalAttributes = personalAttributes

    override fun changePersonalAttributes(personalAttributes: PersonalAttributes) {
        this.personalAttributes = personalAttributes
    }
}
