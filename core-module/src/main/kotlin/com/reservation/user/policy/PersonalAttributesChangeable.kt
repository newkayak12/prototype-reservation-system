package com.reservation.user.policy

import com.reservation.shared.user.PersonalAttributes

interface PersonalAttributesChangeable {

    fun personalAttributes():PersonalAttributes
    fun changePersonalAttributes(personalAttributes: PersonalAttributes)

    fun changeAttributes(email: String, mobile: String){
        changePersonalAttributes(personalAttributes().changePersonalAttributes(email, mobile))
    }

}
