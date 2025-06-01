package com.reservation.user.policy.availables

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
