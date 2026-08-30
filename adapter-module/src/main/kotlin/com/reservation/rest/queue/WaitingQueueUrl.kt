package com.reservation.rest.queue

object WaitingQueueUrl {
    private const val PREFIX = "/api/v1/time-table/booking"

    const val QUEUE = "$PREFIX/{restaurantId:[0-9a-fA-F\\-]{36}}/queue"
    const val QUEUE_TICKET = "$QUEUE/{ticketId}"
}
