package com.reservation.queue.vo

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 대기열이 걸리는 최소 단위(= 예약 슬롯) 식별자.
 *
 * Redis 키(`SEQUENCE:`, `WAITING_QUEUE:`, `ADMITTED:`, `QUEUE_ADMISSION:`)와
 * DB 폴백 테이블(`waiting_queue`) 양쪽이 동일한 식별자를 공유한다.
 */
data class WaitingQueueSlot(
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
) {
    companion object {
        private const val DELIMITER = ":"
        private const val KEY_PART_SIZE = 3
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")

        fun from(key: String): WaitingQueueSlot {
            val parts = key.split(DELIMITER)
            require(parts.size == KEY_PART_SIZE) { "Invalid waiting queue slot key: $key" }

            return WaitingQueueSlot(
                restaurantId = parts[0],
                date = LocalDate.parse(parts[1], DATE_FORMATTER),
                startTime = LocalTime.parse(parts[2], TIME_FORMATTER),
            )
        }
    }

    fun key(): String =
        listOf(
            restaurantId,
            date.format(DATE_FORMATTER),
            startTime.format(TIME_FORMATTER),
        ).joinToString(DELIMITER)
}
