package com.reservation.persistence.user.entity

import com.reservation.config.persistence.entity.TimeBasedUuidStrategy
import com.reservation.enumeration.Role
import com.reservation.persistence.common.AuditDateTime
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(
    catalog = "prototype_reservation",
    name = "user_change_history",
)
@Entity
class UserChangeHistoryEntity(
    uuid: String,
    email: String,
    nickname: String,
    mobile: String,
    role: Role,
    userId: String,
) {
    @Id
    @TimeBasedUuidStrategy
    @Column(name = "id", columnDefinition = "VARCHAR(128)", nullable = false, updatable = false)
    @Comment("식별키")
    val id: String? = null

    @Column(
        name = "user_uuid",
        columnDefinition = "VARCHAR(128)",
        nullable = false,
        updatable = false,
    )
    @Comment("식별키")
    val userUuid: String = uuid

    @Column(name = "user_id", columnDefinition = "VARCHAR(128)")
    @Comment("식별키")
    val userId: String = userId

    @Column(name = "email", columnDefinition = "VARCHAR(32)")
    @Comment("이메일")
    var email: String? = email
        protected set

    @Column(name = "nickname", columnDefinition = "VARCHAR(16)")
    @Comment("닉네임")
    var nickname: String = nickname
        protected set

    @Column(name = "mobile", columnDefinition = "VARCHAR(13)")
    @Comment("휴대폰 번호")
    var mobile: String = mobile
        protected set

    @Column(name = "role", columnDefinition = "ENUM ('ROOT', 'RESTAURANT_OWNER', 'USER')")
    @Enumerated(value = EnumType.STRING)
    @Comment("역할 (ROOT, SELLER, USER)")
    val role: Role = role

    @Embedded
    var auditDateTime = AuditDateTime()
        protected set
}
