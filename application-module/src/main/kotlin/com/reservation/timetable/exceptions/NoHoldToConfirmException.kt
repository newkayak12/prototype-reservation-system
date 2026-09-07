package com.reservation.timetable.exceptions

import com.reservation.exceptions.ClientException

/**
 * 확정할 홀드가 없다.
 *
 * 애초에 좌석을 잡은 적이 없거나, 잡았지만 확정 시간이 지나 스케줄러가 회수했거나, 이미 확정한
 * 경우다. 셋 다 사용자가 지금 할 수 있는 일은 "다시 예약하기"로 같으므로 구분하지 않는다.
 */
class NoHoldToConfirmException(
    message: String = "There is no seat hold to confirm. It may have already expired.",
) : ClientException(message)
