package com.reservation.persistence.user.entity

import com.reservation.enumeration.Role
import com.reservation.persistence.common.AuditDateTime
import com.reservation.persistence.common.TimeBasedPrimaryKey
import com.reservation.persistence.converter.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(
    catalog = "prototype_reservation",
    name = "user_change_history",
)
@Entity
class UserChangeHistoryEntity(
    uuid: String,
    userId: String,
    email: String,
    nickname: String,
    mobile: String,
    role: Role,
) : TimeBasedPrimaryKey() {
    @Convert(converter = UuidBinaryConverter::class)
    @Column(
        name = "user_uuid",
        columnDefinition = "BINARY(16)",
        nullable = false,
        updatable = false,
    )
    @Comment("식별키")
    val userUuid: String = uuid

    // LEAVE-AS-VARCHAR(U1 2a#4): 이 컬럼은 UuidGenerator 값이 아니라
    // user.login_id의 사본이다 — 변환하지 않는다. columnDefinition은 실제
    // DB 컬럼 폭(V1_1__create_user_context.sql:27 `user_id VARCHAR(128)`)을
    // 그대로 따른다 — user.login_id 자신의 컬럼 폭(VARCHAR(32),
    // UserEntity.kt:33-40)과는 다른 수치이니 혼동하지 말 것: 이 컬럼이
    // VARCHAR(32)여야 할 이유가 없고, 실제 스키마와 다른 폭을 선언하면 이
    // 전환과 무관하게 ddl-auto: validate가 깨진다.
    // V1_1_create_user_context.sql:27의 컬럼 코멘트 '식별키'는 오래된/잘못된
    // 주석이다(진짜 UUID 참조는 바로 위 user_uuid) — 과거 마이그레이션
    // 파일은 append-only 원칙상 고치지 않고, 여기 Kotlin 쪽 @Comment로
    // 정확한 의미를 고정한다.
    @Column(name = "user_id", columnDefinition = "VARCHAR(128)")
    @Comment("로그인 아이디 사본(user.login_id) — user_uuid와 달리 UUID 아님")
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
