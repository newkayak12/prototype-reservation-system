package com.reservation.persistence.timetable.entity

import com.reservation.enumeration.TableStatus
import com.reservation.enumeration.TimeTableConfirmStatus
import com.reservation.enumeration.TimeTableConfirmStatus.NOT_CONFIRMED
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Table(
    catalog = "prototype_reservation",
    name = "timetable",
    indexes = [],
)
@Entity
@Suppress("LongParameterList")
class TimeTableEntity(
    @Column(name = "restaurant_id")
    @Comment("매장 ID")
    val restaurantId: String,
    @Column(name = "date")
    @Comment("시간표 날짜")
    val date: LocalDate,
    @field:Enumerated(STRING)
    @Column(name = "day")
    @Comment("시간표 날짜 요일")
    val day: DayOfWeek,
    @Column(name = "start_time")
    @Comment("시작 시간")
    val startTime: LocalTime,
    @Column(name = "end_time")
    @Comment("종료 시간")
    val endTime: LocalTime,
    @Column(name = "table_number")
    @Comment("테이블 번호")
    val tableNumber: Int,
    @Column(name = "table_size")
    @Comment("테이블 사이즈")
    val tableSize: Int,
    @field:Enumerated(STRING)
    @Column(name = "table_status")
    @Comment("테이블 점유 상태")
    var tableStatus: TableStatus,
) : TimeBasedPrimaryKey() {
    @field:Enumerated(STRING)
    @Column(name = "time_table_confirm_status")
    @Comment("테이블 승인 여부")
    var timeTableConfirmStatus: TimeTableConfirmStatus = NOT_CONFIRMED

    fun modifyTableStatus(tableStatus: TableStatus) {
        this.tableStatus = tableStatus
    }
}
