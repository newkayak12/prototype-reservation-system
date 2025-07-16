package com.reservation.restaurant.policy.validations

class RestaurantIntroduceLengthPolicy(
    override val reason: String = "Introduction is too long",
) : RestaurantIntroducePolicy {
    companion object {
        private const val RESTAURANT_INTRODUCTION_MAX_LENGTH = 6000
    }

    override fun validate(introduction: String): Boolean {
        return introduction.length <= RESTAURANT_INTRODUCTION_MAX_LENGTH
    }
}
