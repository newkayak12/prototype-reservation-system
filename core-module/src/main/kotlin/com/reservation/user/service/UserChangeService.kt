package com.reservation.user.service

import com.reservation.user.policy.PasswordChangeable
import com.reservation.user.policy.PersonalAttributesChangeable

class UserChangeService {
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
