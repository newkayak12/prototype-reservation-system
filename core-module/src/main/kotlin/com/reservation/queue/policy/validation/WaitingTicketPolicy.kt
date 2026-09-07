package com.reservation.queue.policy.validation

interface WaitingTicketPolicy {
    val reason: String

    fun validate(target: String): Boolean
}
