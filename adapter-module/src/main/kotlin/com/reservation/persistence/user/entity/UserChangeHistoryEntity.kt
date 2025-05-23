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
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Table(
    catalog = "prototype_reservation",
    name = "user_change_history",
)
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
    @Comment("식별키")
    val id: String? = null

    @ManyToOne(targetEntity = UserEntity::class)
    @JoinColumn(
        updatable = false,
        name = "user_id",
        columnDefinition = "VARCHAR(128)",
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    @Comment("식별키")
    lateinit var userEntity: UserEntity

    @Column(name = "email", columnDefinition = "VARCHAR(32)")
    @Comment("사용자 아이디")
    var email: String? = email
        protected set

    @Column(name = "nickname", columnDefinition = "VARCHAR(16)")
    @Comment("이메일")
    var nickname: String = nickname
        protected set

    @Column(name = "mobile", columnDefinition = "VARCHAR(13)")
    @Comment("닉네임")
    var mobile: String = mobile
        protected set

    @Column(name = "role", columnDefinition = "ENUM ('ROOT', 'RESTAURANT_OWNER', 'USER')")
    @Enumerated(value = EnumType.STRING)
    @Comment("휴대폰 번호")
    val role: Role = role

    @Column(name = "fail_count", columnDefinition = "TINYINT")
    @Comment("역할 (ROOT, SELLER, USER)")
    var failCount: Int = 0
        protected set

    @Column(name = "locked_datetime", columnDefinition = "DATETIME")
    @Comment("로그인 실패 카운트")
    var lockedDatetime: LocalDateTime? = null
        protected set

    @Embedded
    var auditDateTime: AuditDateTime = AuditDateTime()
        protected set
}
