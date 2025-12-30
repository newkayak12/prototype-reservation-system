package com.reservation.persistence.outbox.entity

import com.reservation.enumeration.OutboxEventType
import com.reservation.enumeration.OutboxStatus
import com.reservation.event.abstractEvent.AbstractEvent
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

@Table(name = "outbox")
@Entity
class OutBox(
    eventType: OutboxEventType,
    eventVersion: Double,
    payload: AbstractEvent,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long? = null

    val identifier: Long
        get() = id!!

    @Column(name = "event_version")
    var eventVersion: Double = eventVersion
        protected set

    @Column(name = "event_type")
    @Enumerated(EnumType.STRING)
    var eventType: OutboxEventType = eventType
        protected set

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    var status: OutboxStatus = OutboxStatus.PUBLISHED
        protected set

    @Column(name = "payload", columnDefinition = "JSON")
    @Convert(converter = GenericJson2Converter::class)
    private val payload: AbstractEvent = payload

    @Column(name = "created_at")
    private val createdAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "updated_at")
    private var updatedAt: LocalDateTime? = null

    @Column(name = "count")
    private var count = 0

    fun succeeded() {
        status = OutboxStatus.PROCESSED
        updatedAt = LocalDateTime.now()
        count++
    }

    fun failed() {
        status = OutboxStatus.ERRORED
        updatedAt = LocalDateTime.now()
        count++
    }
}
