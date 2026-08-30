package com.reservation.timetable.port.output

import com.reservation.timetable.TimeTable
import java.time.LocalDate
import java.time.LocalTime

/**
 * 지금 비어 있는 좌석 한 행을 **조건부 갱신으로 가져간다**.
 *
 * ## 왜 [LoadBookableTimeTables]로 충분하지 않은가
 *
 * 저쪽은 목록을 읽기만 한다. 읽는 순간과 저장하는 순간 사이에 다른 트랜잭션이 같은 행을
 * 가져갈 수 있고, 그러면 두 요청이 같은 좌석을 두고 다툰다. 앞단(Redis 카운터 + Kafka 키 순서)이
 * 정상 동작하는 한 그 창은 열리지 않지만, **정상 동작하지 않을 때를 위해 이 포트가 있다.**
 *
 * - Kafka `ProcessingOrder.KEY`는 한 컨슈머 인스턴스 안에서만 완전하다. 앱을 여러 대 띄우고
 *   재시도 토픽 파티션이 다른 인스턴스로 배정되면 같은 슬롯의 두 요청이 겹칠 수 있다.
 * - Redis가 죽으면 좌석 카운터를 믿을 수 없다.
 *
 * 이 장치는 성능을 위한 것이 아니라 **앞의 것들이 틀렸을 때를 위한 것**이다. 정상 경로에서는
 * 슬롯당 한 번에 한 요청만 여기 도달하므로 경합이 없고, 따라서 비용도 거의 없다.
 *
 * ## 반환하는 좌석의 상태
 *
 * 가져가기에 성공한 행을 **가져가기 직전 모습(`EMPTY`)** 그대로 돌려준다. 상태를 `OCCUPIED`로
 * 넘기는 것은 도메인의 몫이고([CreateTimeTableOccupancyDomainService]), 여기서 하는 갱신은
 * 그 전이를 DB 차원에서 한 번만 일어나게 만드는 동시성 장치다. 둘은 같은 전이를 서로 다른
 * 층에서 표현한다.
 *
 * 가져간 행에 걸린 잠금은 호출한 트랜잭션이 끝날 때까지 유지된다 — 이 포트를 부르는 쪽은 반드시
 * 트랜잭션 안이어야 하고, 잡은 행에 대한 저장까지 같은 트랜잭션에서 끝내야 한다.
 */
interface ClaimBookableTimeTable {
    /** @return 가져갈 수 있는 좌석이 하나도 없으면 null. */
    fun claim(inquiry: ClaimBookableTimeTableInquiry): TimeTable?

    data class ClaimBookableTimeTableInquiry(
        val restaurantId: String,
        val date: LocalDate,
        val startTime: LocalTime,
    )
}
