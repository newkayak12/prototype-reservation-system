package com.reservation.user.service

import com.reservation.user.policy.availables.PasswordChangeable
import com.reservation.user.policy.availables.PersonalAttributesChangeable

class ChangeUserService {
    fun <T : PasswordChangeable> changePassword(
        target: T,
        encodedPassword: String,
    ): T = target.apply { changePassword(encodedPassword) }

    fun <T : PersonalAttributesChangeable> changePersonalAttributes(
        target: T,
        email: String,
        mobile: String,
    ): T = target.apply { changePersonalAttributes(email, mobile) }
}
