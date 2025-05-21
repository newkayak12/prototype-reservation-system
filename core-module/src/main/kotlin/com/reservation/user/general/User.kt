package com.reservation.user.general

import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.user.policy.PasswordChangeable
import com.reservation.user.policy.PersonalAttributesChangeable
import com.reservation.user.policy.Withdrawable
import com.reservation.user.widthdrawal.WithdrawalUser

class User (
    private val id: String,
    private var password: Password,
    private var personalAttributes: PersonalAttributes,
): Withdrawable, PasswordChangeable, PersonalAttributesChangeable {



    override fun withdraw(): WithdrawalUser {
        TODO("Not yet implemented")
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
