package com.reservation.persistence.user.entity

import com.reservation.config.persistence.entity.TimeBasedUuidStrategy
import com.reservation.enumeration.Role
import com.reservation.persistence.common.AuditDateTime
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Table(name = "user_change_history")
@Entity
class UserChangeHistoryEntity(
    password: String,
    email: String,
    nickname: String,
    mobile: String,
    role: Role,
) {
    @Id
    @TimeBasedUuidStrategy
    @Column(name = "id", columnDefinition = "VARCHAR(128)", nullable = false, updatable = false)
    val id: String? = null

    @ManyToOne(targetEntity = UserEntity::class)
    @JoinColumn(
        updatable = false,
        name = "user_id",
        columnDefinition = "VARCHAR(128)",
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    lateinit var userEntity: UserEntity

    @Column(name = "email", columnDefinition = "VARCHAR(32)")
    var email: String? = email
        protected set

    @Column(name = "nickname", columnDefinition = "VARCHAR(16)")
    var nickname: String = nickname
        protected set

    @Column(name = "mobile", columnDefinition = "VARCHAR(13)")
    var mobile: String = mobile
        protected set

    @Column(name = "role", columnDefinition = "ENUM ('ROOT', 'RESTAURANT_OWNER', 'USER')")
    @Enumerated(value = EnumType.STRING)
    val role: Role = role

    @Column(name = "fail_count", columnDefinition = "TINYINT")
    var failCount: Int = 0
        protected set

    @Column(name = "locked_datetime", columnDefinition = "DATETIME")
    var lockedDatetime: LocalDateTime? = null
        protected set

    @Embedded
    var auditDateTime: AuditDateTime = AuditDateTime()
        protected set
}
