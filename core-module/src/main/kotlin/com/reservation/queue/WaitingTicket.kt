package com.reservation.queue

import com.reservation.enumeration.QueueStatus
import com.reservation.enumeration.QueueStatus.ADMITTED
import com.reservation.enumeration.QueueStatus.WAITING
import com.reservation.queue.vo.WaitingQueueSlot

/**
 * 대기열 티켓(Aggregate Root).
 *
 * `ticketId`는 (userId + 슬롯) 조합으로부터 결정적으로 파생되므로, 같은 사용자가 같은 슬롯에
 * 다시 진입해도 동일한 티켓이 만들어진다(멱등한 대기열 진입). Phase 2의 DEDUP 키,
 * Phase 4의 `RESULT:{ticketId}` 키가 이 성질에 의존한다.
 */
class WaitingTicket(
    val ticketId: String,
    val slot: WaitingQueueSlot,
    val userId: String,
    private var status: QueueStatus = WAITING,
    private var position: Long? = null,
) {
    val getStatus: QueueStatus
        get() = status

    val getPosition: Long?
        get() = position

    fun enqueued(position: Long) {
        this.status = WAITING
        this.position = position
    }

    fun admitted() {
        this.status = ADMITTED
        this.position = null
    }
}
