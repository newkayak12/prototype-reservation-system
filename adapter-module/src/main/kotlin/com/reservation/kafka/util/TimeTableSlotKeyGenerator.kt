package com.reservation.kafka.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 파티션 키를 만든다.
 *
 * Redis 쪽 `SeatKeyGenerator`/`WaitingQueueKeyGenerator`와 **같은 슬롯 표기**를 쓴다. 표기가
 * 갈라지면 로그와 메트릭에서 같은 슬롯을 두 이름으로 보게 되고, 장애를 추적할 때 그 둘이
 * 같은 것인지 확인하는 데 시간을 쓰게 된다.
 */
object TimeTableSlotKeyGenerator {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm")

    fun slot(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
    ): String = "$restaurantId:${date.format(DATE_FORMAT)}:${startTime.format(TIME_FORMAT)}"
}
