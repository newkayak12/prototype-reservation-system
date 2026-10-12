package com.reservation.persistence.restaurant.entity

import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalTime

@Table(
    catalog = "prototype_reservation",
    name = "restaurant_working_day",
    indexes = [],
)
@Entity
class RestaurantWorkingDayEntity(
    @EmbeddedId
    val id: RestaurantWorkingDayId,
    // ADR-028 결정 3 / inventory 3a#1: @MapsId가 공유하는 이 FK 컬럼은
    // 참조되는 PK의 JDBC 타입(BINARY)은 물려받지만 길이는 안 물려받는다 —
    // BinaryPkHibernateVerificationTest로 확인: columnDefinition 없이는
    // Hibernate가 BINARY(255)를 생성한다(UserType의 기본 길이 255). 바이트
    // 변환 자체는 맞지만 ddl-auto: validate가 실제 BINARY(16) 스키마와
    // 불일치로 실패한다. 그래서 이 JoinColumn은 "순수 암묵 상속"이 아니라
    // 명시적으로 columnDefinition을 재선언해야 하는 사이트다.
    @MapsId("identifier")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "restaurant_id",
        columnDefinition = "BINARY(16)",
        insertable = true,
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(value = NO_CONSTRAINT),
    )
    val restaurant: RestaurantEntity,
    @Column(name = "start_time")
    @Comment("시작 시간")
    var startTime: LocalTime,
    @Column(name = "end_time")
    @Comment("종료 시간")
    var endTime: LocalTime,
)
