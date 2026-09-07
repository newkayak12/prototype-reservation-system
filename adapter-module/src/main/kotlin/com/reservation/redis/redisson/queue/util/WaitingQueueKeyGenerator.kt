package com.reservation.redis.redisson.queue.util

import com.reservation.queue.vo.WaitingQueueSlot

/**
 * 대기열이 쓰는 Redis 키 모음.
 *
 * [admission]은 좌석 세마포어(`SEMAPHORE_SEMAPHORE:{key}`)와 완전히 별개인
 * "동시 입장 허용치" permit pool이다. 접두어(`QUEUE_ADMISSION:`)가 다르므로 좌석 세마포어와
 * 절대 충돌하지 않는다.
 */
object WaitingQueueKeyGenerator {
    private const val SEQUENCE_PREFIX = "SEQUENCE:"
    private const val WAITING_QUEUE_PREFIX = "WAITING_QUEUE:"
    private const val ADMITTED_PREFIX = "ADMITTED:"
    private const val ADMISSION_PREFIX = "QUEUE_ADMISSION:"
    private const val TICKET_OF_PREFIX = "TICKET_OF:"
    private const val PERMIT_OF_PREFIX = "PERMIT_OF:"
    private const val RESULT_PREFIX = "RESULT:"
    private const val DELIMITER = ":"

    const val SLOTS = "QUEUE_SLOTS"

    fun sequence(slot: WaitingQueueSlot) = "$SEQUENCE_PREFIX${slot.key()}"

    fun waitingQueue(slot: WaitingQueueSlot) = "$WAITING_QUEUE_PREFIX${slot.key()}"

    fun admitted(slot: WaitingQueueSlot) = "$ADMITTED_PREFIX${slot.key()}"

    fun admission(slot: WaitingQueueSlot) = "$ADMISSION_PREFIX${slot.key()}"

    /**
     * 한 사용자가 한 슬롯에서 쥘 수 있는 티켓을 하나로 묶는 인덱스. 값은 ticketId다.
     *
     * ticketId가 서버 발급 nonce라 요청마다 값이 달라지므로, 진입의 멱등성은 티켓이 아니라
     * 이 키의 선점(`SET NX`)으로 만들어진다. 결정적 해시를 쓰던 시절에는 "같은 입력 → 같은
     * 티켓"이 그 역할을 공짜로 해주고 있었다.
     *
     * 예약 게이트도 이 키를 읽는다 — 클라이언트가 보낸 ticketId를 믿지 않고 인증에서 얻은
     * userId로 서버에서 티켓을 되찾아오기 위해서다.
     */
    fun ticketOf(
        slot: WaitingQueueSlot,
        userId: String,
    ) = "$TICKET_OF_PREFIX${slot.key()}$DELIMITER$userId"

    /**
     * 이 티켓이 빌린 입장 permit의 ID.
     *
     * `RPermitExpirableSemaphore.tryAcquire()`가 돌려주는 permitId를 보관해 두는 자리다.
     * 이 값이 없으면 permit을 **되돌려 줄 방법이 없어** lease가 만료될 때까지(기본 5분) 자리가
     * 묶인다. 예약을 마친 사용자는 더 이상 입장 자리를 차지할 이유가 없는데도, 다음 사람은
     * 그 5분을 그대로 기다리게 된다.
     */
    fun permitOf(
        slot: WaitingQueueSlot,
        ticketId: String,
    ) = "$PERMIT_OF_PREFIX${slot.key()}$DELIMITER$ticketId"

    fun result(ticketId: String) = "$RESULT_PREFIX$ticketId"
}
