package com.reservation.enumeration

enum class OutboxEventType(
    val topic: String,
) {
    TIME_TABLE_OCCUPIED("time-table-occupied"),
}
