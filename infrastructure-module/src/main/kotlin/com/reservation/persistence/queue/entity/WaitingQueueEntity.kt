package com.reservation.persistence.queue.entity

import com.reservation.enumeration.QueueStatus
import com.reservation.enumeration.QueueStatus.ADMITTED
import com.reservation.enumeration.QueueStatus.WAITING
import com.reservation.persistence.converter.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Redis 장애 시 대기열을 대신 받아내는 테이블.
 * auto-increment [id]가 Redis `INCR SEQUENCE:{key}`의 시퀀스 번호 역할을 한다.
 */
@Table(
    catalog = "prototype_reservation",
    name = "waiting_queue",
    indexes = [],
)
@Entity
class WaitingQueueEntity(
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "restaurant_id", columnDefinition = "BINARY(16)")
    @Comment("매장 ID")
    val restaurantId: String,
    @Column(name = "date")
    @Comment("시간표 날짜")
    val date: LocalDate,
    @Column(name = "start_time")
    @Comment("시작 시간")
    val startTime: LocalTime,
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    @Comment("사용자 ID")
    val userId: String,
    // LEAVE-AS-VARCHAR(U1 2d#17): SecureRandom 기반 32자 hex nonce — UuidGenerator 값이 아니다.
    // 변환하지 않는다. length=128 유지(실제 폭은 32자지만 원래부터 이 선언이었고, 이번 전환의
    // 대상이 아니므로 폭 표현은 U1 범위 밖 — 손대지 않는다).
    @Column(name = "ticket_id", length = 128)
    @Comment("대기열 티켓 ID")
    val ticketId: String,
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id")
    @Comment("대기열 순번")
    val id: Long? = null

    @field:Enumerated(STRING)
    @Column(name = "status")
    @Comment("대기열 상태")
    var status: QueueStatus = WAITING
        protected set

    @Column(name = "admitted_at")
    @Comment("입장 허용 시각")
    var admittedAt: LocalDateTime? = null
        protected set

    @Column(name = "created_at")
    @Comment("대기열 진입 시각")
    val createdAt: LocalDateTime = LocalDateTime.now()

    fun admit(admittedAt: LocalDateTime) {
        this.status = ADMITTED
        this.admittedAt = admittedAt
    }
}
