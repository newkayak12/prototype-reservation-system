package com.reservation.queue.policy.validation

class WaitingTicketRestaurantIdIsNotEmptyValidationPolicy(
    override val reason: String = "Restaurant id must not be empty.",
) : WaitingTicketPolicy {
    override fun validate(target: String): Boolean = target.isNotBlank()
}
