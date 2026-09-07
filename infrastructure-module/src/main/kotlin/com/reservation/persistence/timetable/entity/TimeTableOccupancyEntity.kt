package com.reservation.persistence.timetable.entity

import com.reservation.enumeration.OccupyStatus
import com.reservation.enumeration.OccupyStatus.CONFIRMED
import com.reservation.enumeration.OccupyStatus.PENDING
import com.reservation.enumeration.OccupyStatus.UNOCCUPIED
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType.EAGER
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

/**
 * 좌석 점유.
 *
 * 새로 만들어지는 점유는 [PENDING]에서 시작한다 — 좌석을 잡은 것과 사용자가 그 좌석을 쓰겠다고
 * 확정한 것은 다른 사건이기 때문이다. 확정되지 않은 홀드는 만료 스케줄러가 회수한다.
 *
 * `active_marker`는 DB 생성 컬럼이라 여기에 매핑하지 않는다. `released_at`이 NULL일 때만 1이
 * 되고, 그 위에 걸린 `UNIQUE(timetable_id, active_marker)`가 "같은 좌석에 살아 있는 점유는
 * 하나"를 강제한다. 애플리케이션이 어떤 경로로 실수하든 이 제약은 DB에서 먼저 걸린다.
 */
@Table(
    catalog = "prototype_reservation",
    name = "timetable_occupancy",
    indexes = [],
)
@Entity
@DynamicUpdate
class TimeTableOccupancyEntity(
    @JoinColumn(
        name = "timetable_id",
        foreignKey = ForeignKey(NO_CONSTRAINT),
    )
    @ManyToOne(fetch = EAGER)
    val timeTable: TimeTableEntity,
    @Column(name = "user_id", length = 128)
    val userId: String,
) : TimeBasedPrimaryKey() {
    @Enumerated(STRING)
    @Column(name = "occupied_status")
    var occupiedStatus: OccupyStatus = PENDING
        protected set

    @Column(name = "occupied_datetime")
    val occupiedDatetime: LocalDateTime = LocalDateTime.now()

    @Column(name = "unoccupied_datetime")
    var unoccupiedDatetime: LocalDateTime? = null
        protected set

    /** 점유가 풀린 시각. NULL이면 아직 살아 있다 — 유니크 제약이 보는 값이다. */
    @Column(name = "released_at")
    var releasedAt: LocalDateTime? = null
        protected set

    fun isPending(): Boolean = occupiedStatus == PENDING

    /**
     * 사용자가 확정했다. `released_at`은 건드리지 않는다 — 확정된 점유도 여전히 살아 있고,
     * 그래서 유니크 제약의 보호를 계속 받아야 한다.
     */
    fun confirm() {
        occupiedStatus = CONFIRMED
    }

    /**
     * 홀드를 풀어 좌석을 돌려준다. `released_at`을 채우는 순간 `active_marker`가 NULL이 되어
     * 유니크 제약에서 빠지므로, 같은 좌석에 새 점유가 들어올 수 있게 된다. 행 자체는 남기 때문에
     * "언제 왜 풀렸는지"가 이력으로 보존된다.
     */
    fun release() {
        occupiedStatus = UNOCCUPIED
        val now = LocalDateTime.now()
        unoccupiedDatetime = now
        releasedAt = now
    }

    fun unoccupied() {
        release()
    }
}
