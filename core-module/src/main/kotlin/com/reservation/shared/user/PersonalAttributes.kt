package com.reservation.shared.user

data class PersonalAttributes(
    private val email: String,
    private val mobile: String
) {

    fun updatePersonalAttributes(email: String, mobile: String): PersonalAttributes {
        return PersonalAttributes(
            email,
            mobile
        )
    }
}
