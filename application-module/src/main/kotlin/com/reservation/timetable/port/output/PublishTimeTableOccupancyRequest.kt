package com.reservation.timetable.port.output

import java.time.LocalDate
import java.time.LocalTime

/**
 * 좌석을 확보한 요청을 "나중에 저장해 달라"고 뒤로 넘긴다.
 *
 * ## 왜 동기 저장을 그만두는가
 *
 * 이전에는 API 스레드가 `@Transactional` 안에서 timetable 조회 → occupancy INSERT → outbox INSERT까지
 * 끝내고 나서야 응답했다. 좌석 경합의 승패는 이미 Redis에서 갈렸는데도, 사용자는 DB 쓰기가 끝날
 * 때까지 기다린 셈이다. 부하가 오르면 커넥션 풀이 먼저 마르고 그 대기가 곧 응답시간이 된다.
 *
 * 이 포트를 통과하는 순간 API의 일은 끝난다. 실제 저장은 컨슈머가 맡는다.
 *
 * ## 왜 굳이 Kafka인가 — 순서 때문이다
 *
 * 단순히 비동기로 던지고 싶은 것이라면 스레드풀로도 된다. Kafka를 쓰는 이유는 **파티션 키 단위
 * 순서 보장** 때문이다. 키를 슬롯으로 잡으면 같은 슬롯에 대한 요청들이 한 줄로 세워지고,
 * 컨슈머는 이전 요청의 커밋이 끝난 뒤에야 다음 요청을 본다. 그래서 Phase 4가 소비 시점에
 * "지금 비어 있는 아무 좌석 행 하나"를 새로 뽑아도 두 요청이 같은 행을 두고 다투지 않는다.
 * 이 성질이 없으면 컨슈머가 병렬로 같은 행을 집어 서로를 덮어쓴다.
 *
 * ## 발행 실패는 예외가 아니라 `false`다
 *
 * 발행 실패는 "좌석은 잡았는데 뒷단으로 넘기지 못한" 상태라 호출자가 반드시 좌석을 되돌려야
 * 한다. 그 판단을 하려면 실패를 알아야 하는데, 여기서 `KafkaException`을 그대로 던지면
 * 애플리케이션 계층이 메시징 기술을 알게 된다. 그래서 성공 여부만 Boolean으로 넘긴다.
 */
interface PublishTimeTableOccupancyRequest {
    /** @return 브로커가 수신을 확인했으면 true. */
    fun publish(request: TimeTableOccupancyRequest): Boolean

    /**
     * 특정 `timetableId`를 담지 않는다는 점이 중요하다.
     *
     * "이 슬롯에 한 자리"까지만 정해 두고 "어느 행을 줄지"는 소비 시점에 정한다. 발행 시점에
     * 행을 골라 두면 그 행이 소비될 때까지 비어 있으리라는 보장이 없고, 두 요청이 같은 행을
     * 미리 찍어 둘 수도 있다.
     */
    data class TimeTableOccupancyRequest(
        val restaurantId: String,
        val date: LocalDate,
        val startTime: LocalTime,
        val userId: String,
    )
}
