package com.reservation.persistence.withdrawal.entity

import com.reservation.config.persistence.entity.TimeBasedUuidStrategy
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(name = "withdrawal_user")
@Entity
class WithdrawalUserEntity(
    loginId: String,
    encryptedEmail: String,
    encryptedNickname: String,
    encryptedMobile: String,
    encryptedRole: String,
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

    @Column(name = "encrypted_email", columnDefinition = "VARCHAR(256)")
    @Comment("휴대폰 번호")
    val encryptedEmail: String = encryptedEmail

    @Column(name = "encrypted_nickname", columnDefinition = "VARCHAR(256)")
    @Comment("역할 (ROOT, SELLER, USER)")
    val encryptedNickname: String = encryptedNickname

    @Column(name = "encrypted_mobile", columnDefinition = "VARCHAR(256)")
    @Comment("로그인 실패 카운트")
    val encryptedMobile: String = encryptedMobile

    @Column(name = "encrypted_role", columnDefinition = "VARCHAR(256)")
    @Comment("접근 잠긴 날짜-시간")
    val encryptedRole: String = encryptedRole
}
