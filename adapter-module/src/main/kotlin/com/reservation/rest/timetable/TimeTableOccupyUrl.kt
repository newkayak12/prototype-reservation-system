package com.reservation.rest.timetable

object TimeTableOccupyUrl {
    private const val PREFIX = "/api/v1/time-table/booking"
    const val BOOKING = "$PREFIX/{restaurantId:[0-9a-fA-F\\-]{36}}"

    /** 임시로 잡아 둔 좌석의 확정. 정해진 시간 안에 부르지 않으면 스케줄러가 회수한다. */
    const val BOOKING_CONFIRM = "$BOOKING/confirm"
}
