package com.reservation.queue.port.output

import com.reservation.queue.vo.WaitingQueueSlot

/**
 * 현재 대기열이 살아있는 슬롯 목록. 입장 허용 워커가 순회할 대상이다.
 */
interface LoadWaitingQueueSlots {
    fun query(): List<WaitingQueueSlot>
}
