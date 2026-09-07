package com.reservation.redis.redisson.timetable.seat.script

/**
 * 좌석 재고 Lua 스크립트.
 *
 * Redis는 스크립트를 단일 스레드로 끝까지 실행한다. 아래 스크립트들이 정합성을 책임질 수
 * 있는 근거가 이것이다 — 커맨드 사이에 다른 요청이 끼어들 수 없으므로 분산락으로 요청을
 * 줄 세우지 않아도 된다.
 */
object SeatScripts {
    /**
     * 중복 예약 차단 + 좌석 한 자리 차감.
     *
     * 두 가지를 굳이 한 덩어리로 묶은 이유가 있다. 나눠 부르면 "중복이 아님을 확인했다"와
     * "자리를 잡았다" 사이에 창이 생기고, 그 창으로 같은 사용자의 두 번째 요청이 들어오면
     * 한 사람이 두 자리를 먹는다.
     *
     * - KEYS[1] `DEDUP:{restaurantId}:{date}:{startTime}:{userId}`
     * - KEYS[2] `SEATS:{restaurantId}:{date}:{startTime}`
     * - ARGV[1] 중복 마커 수명 (millis)
     * - ARGV[2] 좌석 카운터 초기값 (예약 가능한 timetable 개수)
     * - ARGV[3] 좌석 카운터 수명 (millis)
     *
     * 반환: 0 = 확보, 1 = 중복, 2 = 품절
     */
    val ACQUIRE =
        """
        -- 1) 슬롯당 1인 1예약. 마커를 먼저 심어야 같은 사용자의 동시 요청 중 하나만 통과한다.
        if not redis.call('SET', KEYS[1], '1', 'NX', 'PX', ARGV[1]) then
            return 1
        end

        -- 2) 좌석 카운터가 없으면 이번에 심는다. NX라서 먼저 심은 값이 언제나 이긴다.
        redis.call('SET', KEYS[2], ARGV[2], 'NX', 'PX', ARGV[3])

        -- 3) 차감. DECR은 원자적이라 동시에 100명이 들어와도 좌석 수만큼만 0 이상을 받는다.
        local remain = redis.call('DECR', KEYS[2])
        if remain < 0 then
            -- 품절. 깎은 값을 되돌리고(카운터가 음수로 흘러내리지 않게)
            -- 마커도 지워 재시도를 허용한다.
            redis.call('INCR', KEYS[2])
            redis.call('DEL', KEYS[1])
            return 2
        end

        return 0
        """.trimIndent()

    /**
     * [ACQUIRE]로 잡은 자리를 되돌린다 — 좌석을 한 자리 돌려주고 중복 마커를 지운다.
     *
     * - KEYS[1] `DEDUP:{restaurantId}:{date}:{startTime}:{userId}`
     * - KEYS[2] `SEATS:{restaurantId}:{date}:{startTime}`
     *
     * 반환: 되돌린 뒤의 잔여 좌석 수 (카운터가 이미 사라졌으면 -1)
     */
    val RELEASE =
        """
        -- 좌석 카운터가 TTL로 이미 사라졌다면 INCR이 0에서 시작해 1이 되어, 있지도 않은
        -- 자리를 만들어내게 된다. 카운터가 살아있을 때만 되돌린다.
        local remain = -1
        if redis.call('EXISTS', KEYS[2]) == 1 then
            remain = redis.call('INCR', KEYS[2])
        end

        redis.call('DEL', KEYS[1])

        return remain
        """.trimIndent()
}
