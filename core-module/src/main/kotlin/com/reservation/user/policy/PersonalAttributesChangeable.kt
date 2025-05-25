package com.reservation.user.policy

import com.reservation.shared.user.PersonalAttributes

interface PersonalAttributesChangeable {
    fun personalAttributes(): PersonalAttributes

    fun changePersonalAttributes(personalAttributes: PersonalAttributes)

    fun changePersonalAttributes(
        email: String,
        mobile: String,
    ) {
        changePersonalAttributes(personalAttributes().updatePersonalAttributes(email, mobile))
    }
}
