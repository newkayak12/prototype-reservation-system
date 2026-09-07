package com.reservation.queue.policy.validation

class WaitingTicketUserIdIsNotEmptyValidationPolicy(
    override val reason: String = "User id must not be empty.",
) : WaitingTicketPolicy {
    override fun validate(target: String): Boolean = target.isNotBlank()
}
