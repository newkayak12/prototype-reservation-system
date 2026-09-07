package com.reservation.persistence.timetable.repository.jpa

import com.reservation.persistence.timetable.entity.TimeTableEntity
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.time.LocalTime

interface TimeTableJpaRepository : CrudRepository<TimeTableEntity, String> {
    companion object {
        /**
         * 살아 있는 점유(`released_at IS NULL`)가 없는 좌석만 예약 가능으로 본다.
         *
         * 이전에는 `occupiedStatus = 'OCCUPIED'`인 점유가 없는지를 봤다. 홀드 흐름이 들어오면서
         * 상태가 PENDING/CONFIRMED로 갈라졌기 때문에, 상태를 나열하는 대신 "풀리지 않았는가"
         * 하나로 판단한다 — 나중에 상태가 더 늘어도 이 조건은 그대로 맞는다.
         */
        private const val FIND_BOOKABLE_TIME_STAMP_SQL = """
        SELECT timetable
        FROM TimeTableEntity timetable
        WHERE timetable.restaurantId = :restaurantId
        AND timetable.date = :date
        AND timetable.startTime = :startTime
        AND timetable.tableStatus = 'EMPTY'
        AND timetable.timeTableConfirmStatus = 'NOT_CONFIRMED'
        AND NOT EXISTS (
            SELECT 1 FROM TimeTableOccupancyEntity timetableOccupancy
            WHERE timetableOccupancy.timeTable.identifier = timetable.identifier
            AND timetableOccupancy.releasedAt IS NULL
        )
        """

        /**
         * 좌석 한 행을 **조건부 UPDATE로 가져간다**.
         *
         * `AND tableStatus = 'EMPTY'`가 이 문장의 전부다. 같은 행을 두고 두 트랜잭션이 겹치면
         * 뒤에 온 쪽은 앞 트랜잭션이 커밋할 때까지 이 행의 X-lock을 기다리고, 풀린 뒤 최신 값을
         * 다시 읽어 `EMPTY`가 아님을 보고 **0행을 고친다**. 즉 갱신 건수가 곧 승패다 —
         * 1이면 내가 가져간 것이고, 0이면 남이 먼저 가져간 것이다.
         *
         * `version`을 직접 올린다. 벌크 UPDATE는 낙관적 잠금 버전을 자동으로 올리지 않아서,
         * 그냥 두면 이 행을 미리 읽어 둔 다른 트랜잭션이 옛 버전으로 덮어써도 아무도 모른다.
         */
        private const val CLAIM_TIME_TABLE_SQL = """
        UPDATE TimeTableEntity timetable
        SET timetable.tableStatus = 'OCCUPIED',
        timetable.version = timetable.version + 1
        WHERE timetable.identifier = :identifier
        AND timetable.tableStatus = 'EMPTY'
        """
    }

    @Query(FIND_BOOKABLE_TIME_STAMP_SQL)
    fun findBookableTimeTable(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
    ): List<TimeTableEntity>

    /**
     * 좌석 한 행을 조건부 UPDATE로 가져간다.
     *
     * ## 왜 마지막 방어선이 필요한가
     *
     * 앞단에 Redis 좌석 카운터와 Kafka 키 순서 보장이 있는데도 이 장치를 두는 이유가 있다.
     * 순서 보장은 **한 컨슈머 인스턴스 안에서만** 완전하다. 앱을 여러 대 띄우고 재시도 토픽의
     * 파티션이 다른 인스턴스로 배정되면 같은 슬롯의 두 요청이 겹칠 수 있고, 그때 둘 다
     * "지금 비어 있는 아무 행"으로 같은 행을 집는다. Redis가 죽어 카운터를 못 믿는 상황도 마찬가지다.
     *
     * 여기서 조건부로 갱신하면 그 창이 닫힌다. 앞의 장치들이 전부 실패해도 같은 좌석이 두 번
     * 팔리지는 않는다 — 성능을 위한 장치가 아니라 **틀렸을 때를 위한 장치**다.
     *
     * ## 왜 `SELECT ... FOR UPDATE`가 아닌가
     *
     * 잠그고-읽고-쓰는 세 단계 대신 한 문장으로 끝난다. 왕복이 줄고, "읽은 뒤 쓰기 전에 값이
     * 바뀌었을 수 있다"는 창 자체가 없다 — 조건 검사와 갱신이 같은 문장 안에 있기 때문이다.
     *
     * @return 갱신된 행 수. 1이면 이 호출이 좌석을 가져갔고, 0이면 남이 먼저 가져갔다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(CLAIM_TIME_TABLE_SQL)
    fun claimTimeTable(identifier: String): Int

    fun findTimeTableEntityByIdentifierEquals(identifier: String): TimeTableEntity?
}
