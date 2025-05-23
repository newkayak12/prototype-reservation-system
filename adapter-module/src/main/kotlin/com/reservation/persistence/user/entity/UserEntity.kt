package com.reservation.persistence.user.entity

import com.reservation.config.persistence.entity.TimeBasedUuidStrategy
import com.reservation.enumeration.Role
import com.reservation.enumeration.UserStatus
import com.reservation.persistence.common.AuditDateTime
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Table(
    name = "user",
    indexes = [
        Index(name = "index_login_id_and_role", columnList = "login_id, role"),
    ],
)
@Entity
class UserEntity(
    loginId: String,
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

    @Column(
        name = "login_id",
        columnDefinition = "VARCHAR(32)",
        nullable = false,
        updatable = false,
    )
    @Comment("식별키")
    val loginId: String = loginId

    @Column(name = "password", columnDefinition = "VARCHAR(256)")
    @Comment("사용자 아이디")
    var password: String = password
        protected set

    @Column(name = "old_password", columnDefinition = "VARCHAR(256)")
    @Comment("이메일")
    var oldPassword: String? = null
        protected set

    @Column(name = "password_changed_datetime", columnDefinition = "DATETIME")
    @Comment("닉네임")
    var passwordChangeDateTime: LocalDateTime? = null
        protected set

    @Column(name = "email", columnDefinition = "VARCHAR(32)")
    @Comment("휴대폰 번호")
    var email: String? = email
        protected set

    @Column(name = "nickname", columnDefinition = "VARCHAR(16)")
    @Comment("역할 (ROOT, SELLER, USER)")
    var nickname: String = nickname
        protected set

    @Column(name = "mobile", columnDefinition = "VARCHAR(13)")
    @Comment("로그인 실패 카운트")
    var mobile: String = mobile
        protected set

    @Column(name = "role", columnDefinition = "ENUM ('ROOT', 'RESTAURANT_OWNER', 'USER')")
    @Comment("접근 잠긴 날짜-시간")
    @Enumerated(value = EnumType.STRING)
    val role: Role = role

    @Column(name = "fail_count", columnDefinition = "TINYINT")
    @Comment("생성 날짜-시간")
    var failCount: Int = 0
        protected set

    @Column(name = "locked_datetime", columnDefinition = "DATETIME")
    @Comment("수정 날짜-시간")
    var lockedDatetime: LocalDateTime? = null

    @Column(name = "user_status", columnDefinition = "ENUM ('ACTIVATED', 'DEACTIVATED')")
    @Comment("역할 (ROOT, SELLER, USER)")
    var userStatus: UserStatus = UserStatus.ACTIVATED
        protected set

    @Embedded
    var auditDateTime: AuditDateTime = AuditDateTime()
        protected set
}
