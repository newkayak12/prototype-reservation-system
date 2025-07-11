package com.reservation.restaurant.policy.validations

interface RestaurantIntroducePolicy : RestaurantUnifiedValidationPolicy<String> {
    override fun validate(introduction: String): Boolean
}
