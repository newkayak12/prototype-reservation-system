package com.reservation.persistence.schedule.entity

import com.reservation.enumeration.ScheduleActiveStatus
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import com.reservation.persistence.converter.UuidBinaryIdentifierType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type

/**
 * ADR-028 결정 3, 폴백 B: `schedule.id`는 `restaurant.id`를 복사해 저장하는
 * BINARY(16) 식별키다. `TimeBasedPrimaryKey`를 상속하지 않는 이유는 이
 * 엔티티가 자체적으로 식별키를 생성하지 않고(audit 컬럼도 없음) 항상
 * `restaurant.identifier` 값을 그대로 받아 쓰기 때문이다(V1_24 마이그레이션
 * 참고). `@Id`에는 JPA `@Convert`가 아니라 [UuidBinaryIdentifierType]
 * (Hibernate `UserType`)을 쓴다 — `TimeBasedPrimaryKey.kt`의 주석과
 * `RestaurantWorkingDayId.kt`가 기록한 것과 동일한 제약: `@Convert`는
 * Hibernate 6.6.13에서 `@Id`에 적용되지 않는다(`BinaryPkHibernateVerificationTest`
 * 로 확인 — 평문 문자열이 그대로 insert돼 `Data truncation`으로 실패).
 */
@Table(
    catalog = "prototype_reservation",
    name = "schedule",
    indexes = [],
)
@Entity
@Suppress("LongParameterList")
class ScheduleEntity(
    @Id
    @Type(UuidBinaryIdentifierType::class)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    val restaurantId: String,
    tablesConfigured: Boolean = false,
    workingHoursConfigured: Boolean = false,
    holidaysConfigured: Boolean = false,
    status: ScheduleActiveStatus = INACTIVE,
    totalTables: Int = 0,
    totalCapacity: Int = 0,
) {
    @Column(name = "tables_configured")
    var tablesConfigured: Boolean = tablesConfigured
        protected set

    @Column(name = "working_hours_configured")
    var workingHoursConfigured: Boolean = workingHoursConfigured
        protected set

    @Column(name = "holidays_configured")
    var holidaysConfigured: Boolean = holidaysConfigured
        protected set

    @Column(name = "status")
    @field:Enumerated(EnumType.STRING)
    var status: ScheduleActiveStatus = status
        protected set

    @Column(name = "total_tables")
    var totalTables: Int = totalTables
        protected set

    @Column(name = "total_capacity")
    var totalCapacity: Int = totalCapacity
        protected set

    fun checkTablesConfigured(count: Int) {
        this.tablesConfigured = count > 0
        toggleActiveState()
    }

    fun checkWorkingHoursConfigured(count: Int) {
        this.workingHoursConfigured = count > 0
        toggleActiveState()
    }

    fun checkHolidaysConfigured(count: Int) {
        this.holidaysConfigured = count > 0
        toggleActiveState()
    }

    fun adjustTableInformation(
        totalTables: Int,
        totalCapacity: Int,
    ) {
        this.totalTables = totalTables
        this.totalCapacity = totalCapacity
    }

    private fun toggleActiveState() {
        this.status =
            this.status.rebalance(
                this.tablesConfigured,
                this.workingHoursConfigured,
                this.holidaysConfigured,
            )
    }
}
