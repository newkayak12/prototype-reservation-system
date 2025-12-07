package com.reservation.persistence.outbox

import com.reservation.enumeration.OutboxEventType
import com.reservation.enumeration.OutboxStatus
import com.reservation.persistence.converter.GenericJson2Converter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Table
@Entity
class OutBox<T>(
    eventType: OutboxEventType,
    payload: T,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long? = null

    @Column(name = "event_type")
    @Enumerated(EnumType.STRING)
    private var eventType: OutboxEventType = eventType

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private var status: OutboxStatus = OutboxStatus.PUBLISHED

    @Column(name = "payload", columnDefinition = "JSON")
    @Convert(converter = GenericJson2Converter::class)
    private val payload: T = payload

    @Column(name = "created_at")
    private val createdAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "updated_at")
    private var updatedAt: LocalDateTime? = null

    fun succeeded() {
        status = OutboxStatus.PROCESSED
        updatedAt = LocalDateTime.now()
    }

    fun failed() {
        status = OutboxStatus.ERRORED
        updatedAt = LocalDateTime.now()
    }
}
