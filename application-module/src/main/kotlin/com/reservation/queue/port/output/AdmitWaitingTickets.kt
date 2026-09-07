package com.reservation.queue.port.output

import com.reservation.queue.vo.WaitingQueueSlot
import java.time.Duration

/**
 * 한 슬롯의 대기열에서 "동시 입장 허용치(세마포어 permit)"가 남아있는 만큼
 * 먼저 온 순서(낮은 score)대로 `ADMITTED:{key}` SET으로 승격시킨다.
 *
 * 승격 자체는 permit 획득 → 대기열 pop(원자적) 순으로 진행되어야 하며,
 * 그 순서 보장은 어댑터 구현의 책임이다.
 */
interface AdmitWaitingTickets {
    fun admit(inquiry: AdmitInquiry): Int

    data class AdmitInquiry(
        val slot: WaitingQueueSlot,
        val capacity: Int,
        val admissionTimeToLive: Duration,
    )
}
