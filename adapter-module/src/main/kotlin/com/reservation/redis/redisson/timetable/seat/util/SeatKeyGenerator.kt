package com.reservation.redis.redisson.timetable.seat.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 좌석 재고/중복 차단이 쓰는 Redis 키.
 *
 * 슬롯 표기(`{restaurantId}:{yyyyMMdd}:{HHmm}`)는 대기열(`WaitingQueueSlot.key()`)과 같은 규칙을
 * 쓴다 — 같은 슬롯을 두 컨텍스트가 서로 다른 문자열로 부르면 운영 중에 키를 눈으로 대조할 수
 * 없게 된다. 다만 접두어가 달라 키 공간은 겹치지 않는다.
 */
object SeatKeyGenerator {
    private const val SEATS_PREFIX = "SEATS:"
    private const val DEDUP_PREFIX = "DEDUP:"
    private const val DELIMITER = ":"
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")

    private fun slot(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
    ) = listOf(
        restaurantId,
        date.format(DATE_FORMATTER),
        startTime.format(TIME_FORMATTER),
    ).joinToString(DELIMITER)

    fun seats(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
    ) = "$SEATS_PREFIX${slot(restaurantId, date, startTime)}"

    fun dedup(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
        userId: String,
    ) = "$DEDUP_PREFIX${slot(restaurantId, date, startTime)}$DELIMITER$userId"
}
