package com.reservation.user.owner

import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.user.policy.PasswordChangeable
import com.reservation.user.policy.PersonalAttributesChangeable
import com.reservation.user.policy.UserWithdrawable
import com.reservation.user.widthdrawal.WithdrawalUser

class RestaurantOwner(
    private val id: String,
    private val password: Password,
    private val personalAttributes: PersonalAttributes,
): UserWithdrawable, PasswordChangeable, PersonalAttributesChangeable {

    override fun withdraw(): WithdrawalUser {
        TODO("Not yet implemented")
    }

    override fun changePassword(password: Password) {
        TODO("Not yet implemented")
    }

    override fun password(): Password = password
    override fun personalAttributes(): PersonalAttributes {
        TODO("Not yet implemented")
    }

    override fun changePersonalAttributes(personalAttributes: PersonalAttributes) {
        TODO("Not yet implemented")
    }
}
