package com.reservation.user.shared.vo

data class PersonalAttributes(
    val email: String,
    val mobile: String,
) {
    fun updatePersonalAttributes(
        email: String,
        mobile: String,
    ): PersonalAttributes {
        return PersonalAttributes(
            email,
            mobile,
        )
    }
}
