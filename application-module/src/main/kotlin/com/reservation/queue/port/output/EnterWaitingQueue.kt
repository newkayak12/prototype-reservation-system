package com.reservation.queue.port.output

import com.reservation.queue.vo.WaitingQueueSlot

/**
 * 대기열에 티켓을 밀어 넣고 순번을 돌려준다.
 *
 * 멱등성의 기준은 티켓이 아니라 **사용자**다. `ticketId`가 서버 발급 nonce라 재요청마다 값이
 * 달라지므로, 같은 (slot, userId)로 다시 들어오면 처음 실린 티켓과 순번을 그대로 돌려준다.
 */
interface EnterWaitingQueue {
    companion object {
        /**
         * "이미 입장이 허용되어 기다릴 필요가 없다"를 뜻하는 순번.
         *
         * 대기 순번은 1부터 시작하므로 0은 어떤 대기 상태와도 겹치지 않는다.
         * ADMITTED 된 티켓은 대기열 ZSET에서 이미 ZPOPMIN 되어 rank가 null이므로, 이 값을
         * 쓰지 않으면 재진입한 사용자가 조용히 대기열 뒤에 다시 붙어 정원을 한 번 더 소모한다.
         */
        const val ADMITTED_POSITION = 0L
    }

    fun enter(inquiry: EnterWaitingQueueInquiry): EnteredTicket

    data class EnterWaitingQueueInquiry(
        val slot: WaitingQueueSlot,
        val userId: String,
        /** 이번 요청에서 새로 발급한 후보 nonce. 선점에 실패하면 버려진다. */
        val ticketId: String,
    )

    /**
     * 실제로 대기열에 실린 티켓.
     *
     * [ticketId]는 후보와 다를 수 있다 — 이 사용자가 이미 진입해 있었다면 그때 실린 티켓이
     * 그대로 돌아온다. 호출자는 후보가 아니라 **이 값**을 사용자에게 돌려줘야 한다.
     */
    data class EnteredTicket(
        val ticketId: String,
        val position: Long,
    )
}
