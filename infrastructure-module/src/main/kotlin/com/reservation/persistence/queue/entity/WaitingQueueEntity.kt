package com.reservation.persistence.queue.entity

import com.reservation.enumeration.QueueStatus
import com.reservation.enumeration.QueueStatus.ADMITTED
import com.reservation.enumeration.QueueStatus.WAITING
import jakarta.persistence.Column
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
    @Column(name = "restaurant_id")
    @Comment("매장 ID")
    val restaurantId: String,
    @Column(name = "date")
    @Comment("시간표 날짜")
    val date: LocalDate,
    @Column(name = "start_time")
    @Comment("시작 시간")
    val startTime: LocalTime,
    @Column(name = "user_id", length = 128)
    @Comment("사용자 ID")
    val userId: String,
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
