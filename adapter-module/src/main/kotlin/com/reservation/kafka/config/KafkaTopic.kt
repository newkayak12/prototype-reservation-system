package com.reservation.kafka.config

/**
 * 토픽 이름의 유일한 출처.
 *
 * 이 파일이 생긴 이유가 있다. 프로듀서는 `OutboxEventType.TIME_TABLE_OCCUPIED`의 enum 이름을
 * 토픽으로 썼고, 컨슈머는 자기 클래스 안에 `"time-table-occupancy"`라는 상수를 따로 들고 있었다.
 * 두 이름이 다르니 발행된 메시지를 아무도 구독하지 않았고, 브로커에는 서로 다른 두 토픽이
 * 나란히 만들어졌다. 컴파일도 되고 테스트도 통과하는 종류의 버그다 — 양쪽이 서로를 참조하지
 * 않으니 불일치를 알아챌 방법이 없었다.
 *
 * 그래서 이름을 여기 한 곳에만 둔다.
 */
object KafkaTopic {
    /**
     * 좌석을 확보한 예약 요청.
     *
     * 파티션 키는 슬롯(`restaurantId:yyyyMMdd:HHmm`)이다. 같은 슬롯의 요청이 항상 같은
     * 파티션으로 가고, 컨슈머의 `ProcessingOrder.KEY` 설정과 맞물려 키 단위로 순서가 보장된다.
     * 사용자 단위가 아니라 슬롯 단위로 잡은 이유는, 순서가 필요한 대상이 "한 사람의 요청들"이
     * 아니라 "같은 자리를 두고 다투는 요청들"이기 때문이다.
     */
    const val TIMETABLE_OCCUPANCY_REQUESTED = "TIMETABLE_OCCUPANCY_REQUESTED"

    /** 점유가 저장된 뒤 하류(예약 생성)로 흘러가는 이벤트. */
    const val TIME_TABLE_OCCUPIED = "TIME_TABLE_OCCUPIED"
}
