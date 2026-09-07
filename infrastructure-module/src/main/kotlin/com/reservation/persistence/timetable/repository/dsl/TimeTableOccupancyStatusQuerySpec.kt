package com.reservation.persistence.timetable.repository.dsl

import com.reservation.enumeration.OccupyStatus
import com.reservation.persistence.timetable.entity.QTimeTableOccupancyEntity.timeTableOccupancyEntity

object TimeTableOccupancyStatusQuerySpec {
    fun timeTableOccupancyEq(occupyStatus: OccupyStatus) =
        timeTableOccupancyEntity.occupiedStatus.eq(occupyStatus)

    /**
     * 아직 살아 있는 점유.
     *
     * 상태를 나열해서 판단하지 않는 이유가 있다. 홀드 흐름이 들어오면서 살아 있는 점유가
     * PENDING과 CONFIRMED 둘로 갈라졌고, 예전 데이터에는 OCCUPIED도 있다. 셋을 나열하면 상태가
     * 하나 더 늘 때마다 이 조건을 찾아 고쳐야 하고, 빠뜨리면 조용히 안 잡힌다.
     * "풀리지 않았는가"는 상태가 몇 개로 늘어도 그대로 맞는 질문이다.
     */
    fun timeTableOccupancyIsAlive() = timeTableOccupancyEntity.releasedAt.isNull
}
