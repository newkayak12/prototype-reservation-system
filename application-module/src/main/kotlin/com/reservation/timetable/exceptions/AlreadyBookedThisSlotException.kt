package com.reservation.timetable.exceptions

import com.reservation.exceptions.ClientException

/**
 * 슬롯당 1인 1예약 위반. 이미 예약했거나, 앞선 요청이 아직 처리 중이다.
 *
 * 좌석이 없어서 거절하는 [AllTheSeatsAreAlreadyOccupiedException]과는 원인이 다르다 —
 * 이쪽은 좌석이 남아 있어도 거절되며, 재시도해도 결과가 같다.
 */
class AlreadyBookedThisSlotException(
    message: String = "You have already booked this time slot.",
) : ClientException(message)
