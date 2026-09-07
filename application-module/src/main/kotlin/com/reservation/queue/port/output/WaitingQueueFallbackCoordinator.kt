package com.reservation.queue.port.output

import com.reservation.queue.vo.WaitingQueueSlot
import java.time.Duration

/**
 * Redis 장애 시 사용하는 DB(`waiting_queue`) 기반 대기열 좌표계.
 *
 * `DistributedLockAspect`가 `RedisException`을 만나면 `NamedLockCoordinator`로 갈아타는 것과
 * 동일한 발상이다 — Redis 어댑터(포트 구현체)가 `RedisException`을 잡아 이 좌표계로 위임한다.
 * auto-increment id가 Redis `INCR SEQUENCE:{key}`의 역할을 대신한다.
 */
interface WaitingQueueFallbackCoordinator {
    /**
     * Redis의 `TICKET_OF:{slot}:{userId}` 선점에 대응하는 것은 `waiting_queue`의
     * `(restaurant_id, date, start_time, user_id)` 유니크 키다. 이쪽도 사용자 단위로 멱등해야
     * 폴백 중에 한 사용자가 여러 자리를 잡지 않는다.
     */
    fun enter(
        slot: WaitingQueueSlot,
        userId: String,
        ticketId: String,
    ): EnterWaitingQueue.EnteredTicket

    fun findPosition(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): Long?

    fun isAdmitted(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): Boolean

    /** Redis의 `TICKET_OF` → `ADMITTED` 2단 조회에 대응한다. [IsUserAdmitted] 참고. */
    fun isUserAdmitted(
        slot: WaitingQueueSlot,
        userId: String,
    ): Boolean

    fun admit(
        slot: WaitingQueueSlot,
        capacity: Int,
        admissionTimeToLive: Duration,
    ): Int

    fun loadSlots(): List<WaitingQueueSlot>
}
