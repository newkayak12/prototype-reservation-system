package com.reservation.restaurant.policy.validations

class RestaurantPhoneFormatValidationPolicy(
    override val reason: String = " Invalid phone format",
) : RestaurantPhonePolicy {
    companion object {
        val MOBILE_FORMAT_REG_EXP =
            Regex("^01[016789]-?\\d{3,4}-?\\d{4}$")
        val LOCAL_FORMAT_REG_EXP =
            Regex("^0(2|3[13]|4[23]|5[1-5]|6[2-4])-?\\d{3,4}-?\\d{4}$")
    }

    override fun validate(phoneNumber: String): Boolean =
        MOBILE_FORMAT_REG_EXP.matches(phoneNumber) || LOCAL_FORMAT_REG_EXP.matches(phoneNumber)
}
