package com.reservation.queue.port.input

import com.reservation.queue.vo.WaitingQueueSlot

/**
 * 슬롯 하나의 대기열을 민다 — permit이 남은 만큼 앞에서부터 ADMITTED로 승격시키고,
 * 이번 호출에 승격된 수를 돌려준다.
 *
 * ## 왜 [AdmitWaitingQueueUseCase]와 나누는가
 *
 * 그쪽은 "살아있는 슬롯 전부를 훑는 한 사이클"이고 분산 락이 걸려 있다. 폴링 요청마다
 * 전 슬롯을 훑을 수도, 요청마다 분산 락을 잡을 수도 없다. 그래서 승격의 최소 단위를
 * 여기로 빼고, 워커는 이것을 슬롯마다 반복하는 얇은 껍데기가 된다.
 *
 * 락이 없어도 되는 이유는 승격 자체가 원자적이기 때문이다 — permit 획득과 `ZPOPMIN`이
 * 각각 Redis 단일 커맨드라, 동시에 1,000개의 요청이 이 메소드를 불러도 정원을 넘겨
 * 승격되지 않는다. 락은 애초에 정합성이 아니라 DB 폴백 경로의 중복 작업을 줄이려고
 * 워커에 걸어 둔 것이다.
 */
interface AdmitSlotUseCase {
    fun execute(slot: WaitingQueueSlot): Int
}
