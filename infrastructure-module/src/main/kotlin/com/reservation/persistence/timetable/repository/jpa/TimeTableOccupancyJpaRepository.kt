package com.reservation.persistence.timetable.repository.jpa

import com.reservation.persistence.timetable.entity.TimeTableOccupancyEntity
import jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

interface TimeTableOccupancyJpaRepository : CrudRepository<TimeTableOccupancyEntity, String> {
    companion object {
        /**
         * 이 사용자가 이 슬롯에 쥐고 있는 살아 있는 홀드.
         *
         * 클라이언트가 보낸 점유 ID로 찾지 않는다 — 남의 홀드 ID를 넣어 확정해 버리는 경로가
         * 생긴다. 인증에서 얻은 `userId`와 슬롯만으로 서버가 직접 찾는다. 슬롯당 1인 1예약이
         * 앞단에서 보장되므로 결과는 항상 0건 아니면 1건이다.
         */
        private const val LOCK_ACTIVE_OCCUPANCY_SQL = """
        SELECT occupancy
        FROM TimeTableOccupancyEntity occupancy
        WHERE occupancy.userId = :userId
        AND occupancy.releasedAt IS NULL
        AND occupancy.timeTable.restaurantId = :restaurantId
        AND occupancy.timeTable.date = :date
        AND occupancy.timeTable.startTime = :startTime
        """

        /**
         * 확정되지 않은 채 시간이 지난 홀드.
         *
         * `releasedAt IS NULL`을 같이 보는 이유는, 이미 회수된 행을 스캔이 계속 집어 올리면
         * 스케줄러가 같은 행을 주기마다 다시 처리하기 때문이다.
         */
        private const val FIND_EXPIRED_HOLDS_SQL = """
        SELECT occupancy
        FROM TimeTableOccupancyEntity occupancy
        WHERE occupancy.occupiedStatus = 'PENDING'
        AND occupancy.releasedAt IS NULL
        AND occupancy.occupiedDatetime < :threshold
        ORDER BY occupancy.occupiedDatetime ASC
        """
    }

    @Lock(PESSIMISTIC_WRITE)
    @Query(LOCK_ACTIVE_OCCUPANCY_SQL)
    fun lockActiveOccupancy(
        userId: String,
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
    ): List<TimeTableOccupancyEntity>

    /**
     * 잠그면서 가져온다. 만료 스캔과 사용자의 확정 요청이 같은 행을 두고 만날 수 있는데,
     * 잠그지 않으면 "확정된 직후에 만료 처리되는" 창이 열린다.
     */
    @Lock(PESSIMISTIC_WRITE)
    @Query(FIND_EXPIRED_HOLDS_SQL)
    fun lockExpiredHolds(
        threshold: LocalDateTime,
        pageable: Pageable,
    ): List<TimeTableOccupancyEntity>
}
