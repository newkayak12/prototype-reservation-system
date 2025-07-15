package com.reservation.persistence.withdrawal.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(
    catalog = "prototype_reservation",
    name = "withdrawal_user",
)
@Entity
class WithdrawalUserEntity(
    loginId: String,
    encryptedEmail: String,
    encryptedNickname: String,
    encryptedMobile: String,
    encryptedRole: String,
) : TimeBasedPrimaryKey() {
    @Column(
        name = "login_id",
        columnDefinition = "VARCHAR(32)",
        nullable = false,
        updatable = false,
    )
    @Comment("사용자 아이디")
    val loginId: String = loginId

    @Column(name = "encrypted_email", columnDefinition = "VARCHAR(256)")
    @Comment("이메일")
    val encryptedEmail: String = encryptedEmail

    @Column(name = "encrypted_nickname", columnDefinition = "VARCHAR(256)")
    @Comment("닉네임")
    val encryptedNickname: String = encryptedNickname

    @Column(name = "encrypted_mobile", columnDefinition = "VARCHAR(256)")
    @Comment("휴대폰 번호")
    val encryptedMobile: String = encryptedMobile

    @Column(name = "encrypted_role", columnDefinition = "VARCHAR(256)")
    @Comment("역할 ")
    val encryptedRole: String = encryptedRole
}
