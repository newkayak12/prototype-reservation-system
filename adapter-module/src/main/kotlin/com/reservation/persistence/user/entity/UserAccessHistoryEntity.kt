package com.reservation.persistence.user.entity

import com.reservation.enumeration.AccessStatus
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
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
) : TimeBasedPrimaryKey() {
    @Column(name = "user_uuid", columnDefinition = "VARCHAR(128)")
    @Comment("식별키")
    val userUuid: String = userId

    @Column(name = "access_status", columnDefinition = "ENUM ('SUCCESS', 'FAILURE')")
    @Comment("상태(SUCCESS, FAILURE)")
    val accessStatus: AccessStatus = accessStatus

    @Column(name = "access_datetime", columnDefinition = "DATETIME")
    @Comment("요청 날짜-시간")
    val accessDatetime: LocalDateTime = accessDatetime
}
