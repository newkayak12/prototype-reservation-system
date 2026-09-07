package com.reservation.redis.redisson.timetable.seat.adapter

import com.reservation.redis.redisson.timetable.seat.script.SeatScripts
import com.reservation.redis.redisson.timetable.seat.util.SeatKeyGenerator
import com.reservation.timetable.port.output.ReleaseTimeTableSeat
import com.reservation.timetable.port.output.ReleaseTimeTableSeat.SeatReleaseInquiry
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.redisson.client.codec.StringCodec
import org.springframework.stereotype.Component

/**
 * 좌석 되돌리기.
 *
 * 확보 경로와 달리 여기서는 `RedisException`을 삼킨다. 이 호출은 이미 실패가 확정된 요청을
 * 정리하는 보상 동작이라, 여기서 다시 예외를 던지면 원래의 실패 원인이 가려진다.
 * 되돌리기가 실패해도 좌석 카운터와 중복 마커에는 TTL이 걸려 있어 결국 회수된다.
 */
@Component
class ReleaseTimeTableSeatRedisAdapter(
    private val redissonClient: RedissonClient,
) : ReleaseTimeTableSeat {
    private val log = loggerFactory<ReleaseTimeTableSeatRedisAdapter>()

    override fun release(inquiry: SeatReleaseInquiry) {
        val dedupKey =
            SeatKeyGenerator.dedup(
                inquiry.restaurantId,
                inquiry.date,
                inquiry.startTime,
                inquiry.userId,
            )
        val seatsKey =
            SeatKeyGenerator.seats(inquiry.restaurantId, inquiry.date, inquiry.startTime)

        try {
            redissonClient.getScript(StringCodec.INSTANCE).eval<Long>(
                RScript.Mode.READ_WRITE,
                SeatScripts.RELEASE,
                RScript.ReturnType.INTEGER,
                listOf<Any>(dedupKey, seatsKey),
            )
        } catch (exception: RedisException) {
            log.warn(
                "Failed to release the seat. it will be reclaimed by TTL: {}",
                exception.message,
            )
        }
    }
}
