package com.reservation.queue.port.output

import com.reservation.enumeration.QueueStatus

/**
 * `RESULT:{ticketId}` 키를 조회한다.
 *
 * 이 키는 Phase 4(임시 홀드 → confirm → 만료)가 채운다. Phase 1은 "값이 있으면 그대로
 * 최종 상태로 응답한다"는 조회 경로만 미리 뚫어둔다.
 */
interface FindTicketResult {
    fun query(ticketId: String): QueueStatus?
}
