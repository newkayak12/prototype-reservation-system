package com.reservation.timetable.port.input

/**
 * 확정되지 않은 채 시간이 지난 홀드를 회수한다. 스케줄러가 주기적으로 호출한다.
 *
 * 이 장치가 없으면 "좌석은 잡았는데 확정하지 않고 사라진" 사용자가 그 자리를 영원히 묶는다.
 * 좌석이 30개뿐인 슬롯에서는 몇 명만 그래도 매장이 통째로 팔 수 없는 상태가 된다.
 */
interface ExpireTimeTableHoldsUseCase {
    /** @return 이번 주기에 회수한 홀드 수. */
    fun execute(): Int
}
