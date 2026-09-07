package com.reservation.queue.port.output

import com.reservation.queue.vo.WaitingQueueSlot

/**
 * "이 **사용자**가 지금 이 슬롯에 입장 허용된 상태인가."
 *
 * 예약 엔드포인트의 대기열 강제 게이트가 쓴다. [IsTicketAdmitted]와 묻는 대상이 다르다 —
 * 저쪽은 클라이언트가 자기 티켓의 상태를 폴링하는 용도라 ticketId를 받는 게 맞지만,
 * 게이트는 **클라이언트가 보낸 ticketId를 믿으면 안 된다.** 티켓이 유출되거나 추측되는 순간
 * 게이트가 무력화되기 때문이다.
 *
 * 그래서 게이트는 인증에서 얻은 userId만 넘기고, 티켓을 되찾아오는 일은 어댑터가
 * `TICKET_OF:{slot}:{userId}`를 읽어서 처리한다. ticketId가 결정적 해시였을 때는 서버가
 * 같은 값을 다시 계산할 수 있어서 이 포트가 필요 없었지만, nonce로 바뀌면서 계산이 아니라
 * 조회가 됐다.
 */
interface IsUserAdmitted {
    fun query(inquiry: UserAdmissionInquiry): Boolean

    data class UserAdmissionInquiry(
        val slot: WaitingQueueSlot,
        val userId: String,
    )
}
