package com.reservation.queue.port.input

/**
 * 대기열 입장 허용 워커의 한 사이클. 이번 사이클에 ADMITTED로 승격된 티켓 수를 돌려준다.
 */
interface AdmitWaitingQueueUseCase {
    fun execute(): Int
}
