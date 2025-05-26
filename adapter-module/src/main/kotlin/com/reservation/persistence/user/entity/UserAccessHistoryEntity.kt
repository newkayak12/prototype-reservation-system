package com.reservation.persistence.user.entity

import com.reservation.config.persistence.entity.TimeBasedUuidStrategy
import com.reservation.enumeration.AccessStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Table(
    catalog = "prototype_reservation",
    name = "user_access_history",
)
@Entity
class UserAccessHistoryEntity(
    userId: String,
    accessStatus: AccessStatus,
    accessDatetime: LocalDateTime,
) {
    @Id
    @TimeBasedUuidStrategy
    @Column(name = "id", columnDefinition = "VARCHAR(128)", nullable = false, updatable = false)
    @Comment("식별키")
    val id: String? = null

    @Column(name = "user_id", columnDefinition = "VARCHAR(128)")
    @Comment("식별키")
    val userId: String = userId

    @Column(name = "access_status", columnDefinition = "ENUM ('SUCCESS', 'FAILURE')")
    @Comment("상태(SUCCESS, FAILURE)")
    val accessStatus: AccessStatus = accessStatus

    @Column(name = "access_datetime", columnDefinition = "DATETIME")
    @Comment("요청 날짜-시간")
    val accessDatetime: LocalDateTime = accessDatetime
}
