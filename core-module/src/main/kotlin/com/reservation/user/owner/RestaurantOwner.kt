package com.reservation.user.owner

import com.reservation.encrypt.aes.AESUtility
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
    nickname: String
): UserWithdrawable, PasswordChangeable, PersonalAttributesChangeable {


    private var userAttributes: UserAttribute = UserAttribute(nickname, Role.RESTAURANT_OWNER)


    override fun withdraw(): WithdrawalUser {
        return WithdrawalUser(
            id,
            loginId,
            EncryptedAttributes(
                AESUtility.encrypt(personalAttributes.email),
                AESUtility.encrypt(userAttributes.nickname),
                AESUtility.encrypt(personalAttributes.mobile),
                AESUtility.encrypt(userAttributes.role.name),
            ),
            LocalDateTime.now()
        )
    }

    override fun changePassword(password: Password) {
        this.password = password
    }

    override fun password(): Password = password

    override fun personalAttributes(): PersonalAttributes  = personalAttributes

    override fun changePersonalAttributes(personalAttributes: PersonalAttributes) {
        this.personalAttributes = personalAttributes
    }
}
