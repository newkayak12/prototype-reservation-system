package com.reservation.restaurant.policy.validations

interface RestaurantUnifiedValidationPolicy<T> {
    val reason: String

    fun validate(target: T): Boolean
}
