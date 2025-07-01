package com.reservation.user.service

import com.reservation.user.policy.availables.PersonalAttributesChangeable

class ChangeUserAttributeService {
    fun <T : PersonalAttributesChangeable> changePersonalAttributes(
        target: T,
        email: String,
        mobile: String,
    ): T = target.apply { changePersonalAttributes(email, mobile) }
}
