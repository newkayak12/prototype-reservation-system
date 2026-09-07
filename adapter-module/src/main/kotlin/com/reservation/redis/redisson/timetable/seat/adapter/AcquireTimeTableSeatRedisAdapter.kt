package com.reservation.redis.redisson.timetable.seat.adapter

import com.reservation.redis.redisson.timetable.seat.script.SeatScripts
import com.reservation.redis.redisson.timetable.seat.util.SeatKeyGenerator
import com.reservation.timetable.port.output.AcquireTimeTableSeat
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.ACQUIRED
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.DUPLICATED
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.SOLD_OUT
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatInquiry
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * `DEDUP` + `SEATS`를 하나의 Lua 스크립트로 처리하는 좌석 확보 어댑터.
 *
 * ## Redis가 죽으면 어떻게 되는가
 *
 * 여기서는 대기열처럼 DB로 폴백하지 않고 예외를 그대로 올려보낸다(= 요청 실패).
 * 좌석 정합성의 유일한 근거가 이 카운터인데, Redis가 없는 상태에서 "일단 받아 두는" 폴백을
 * 만들면 그 순간 오버부킹을 허용하겠다는 뜻이 된다. 느슨하게 받아 주는 것보다 거절하는 편이
 * 낫다. 최종 방어선(DB row-lock)은 Phase 4에서 들어온다.
 */
@Component
class AcquireTimeTableSeatRedisAdapter(
    private val redissonClient: RedissonClient,
    @Value("\${reservation.timetable.seat-time-to-live-seconds:3600}")
    private val seatTimeToLiveSeconds: Long,
    @Value("\${reservation.timetable.dedup-time-to-live-seconds:3600}")
    private val dedupTimeToLiveSeconds: Long,
) : AcquireTimeTableSeat {
    companion object {
        private const val RESULT_ACQUIRED = 0L
        private const val RESULT_DUPLICATED = 1L
        private const val RESULT_SOLD_OUT = 2L
    }

    override fun acquire(inquiry: SeatInquiry): SeatAcquisition {
        val dedupKey =
            SeatKeyGenerator.dedup(
                inquiry.restaurantId,
                inquiry.date,
                inquiry.startTime,
                inquiry.userId,
            )
        val seatsKey =
            SeatKeyGenerator.seats(inquiry.restaurantId, inquiry.date, inquiry.startTime)

        val result =
            redissonClient.getScript(StringCodec.INSTANCE).eval<Long>(
                RScript.Mode.READ_WRITE,
                SeatScripts.ACQUIRE,
                RScript.ReturnType.INTEGER,
                listOf<Any>(dedupKey, seatsKey),
                Duration.ofSeconds(dedupTimeToLiveSeconds).toMillis().toString(),
                inquiry.availableSeats.toString(),
                Duration.ofSeconds(seatTimeToLiveSeconds).toMillis().toString(),
            )

        return when (result) {
            RESULT_ACQUIRED -> ACQUIRED
            RESULT_DUPLICATED -> DUPLICATED
            RESULT_SOLD_OUT -> SOLD_OUT
            else -> error("Unknown seat acquisition result: $result")
        }
    }
}
