package com.reservation.event.timetable.occupancy.adapter

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.enumeration.OutboxEventType
import com.reservation.enumeration.OutboxEventType.TIME_TABLE_OCCUPIED
import com.reservation.event.abstractEvent.AbstractEvent
import com.reservation.event.timetable.occupancy.TimeTableOccupiedEvent
import com.reservation.event.timetable.occupancy.TimeTableOccupiedOutboxEvent
import com.reservation.persistence.outbox.entity.OutBox
import com.reservation.persistence.outbox.repository.OutboxRepository
import com.reservation.timetable.event.TimeTableOccupiedDomainEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT
import org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT
import org.springframework.transaction.event.TransactionalEventListener
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS

@Component
class TimeTableOccupiedDomainEventListener(
    private val kafkaTemplate: KafkaTemplate<String, AbstractEvent>,
    private val repository: OutboxRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    companion object {
        const val TIME_TABLE_OCCUPIED_EVENT_VERSION = 1.0
        const val KAFKA_EVENT_PUBLISH_TIME_OUT = 10L
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun handleCreateTimeTableOccupancyEvent(
        event: TimeTableOccupiedDomainEvent,
    ): TimeTableOccupiedOutboxEvent {
        val createdEvent =
            event.toKafkaEvent(
                TIME_TABLE_OCCUPIED,
                TIME_TABLE_OCCUPIED_EVENT_VERSION,
            )

        val outbox = createOutbox(createdEvent)

        return TimeTableOccupiedOutboxEvent(outbox.identifier, createdEvent)
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    fun publishKafkaEvent(outboxEvent: TimeTableOccupiedOutboxEvent) {
        val outboxId = outboxEvent.outboxId
        val createdEvent = outboxEvent.event

        val kafkaTopic = createdEvent.eventType.topic
        val kafkaKey = createdEvent.key()

        val outbox =
            repository.findById(outboxId)
                .orElseThrow { throw NoSuchPersistedElementException() }

        runCatching {
            kafkaTemplate.send(kafkaTopic, kafkaKey, createdEvent)
                .get(KAFKA_EVENT_PUBLISH_TIME_OUT, SECONDS)
        }
            .onSuccess { outbox.succeeded() }
            .onFailure { exception ->

                when (exception) {
                    is InterruptedException, is ExecutionException -> outbox.failed()
                    else -> throw exception
                }
            }
    }

    private fun TimeTableOccupiedDomainEvent.toKafkaEvent(
        eventType: OutboxEventType,
        eventVersion: Double,
    ) = TimeTableOccupiedEvent(
        eventType = eventType,
        timeTableId = this.timeTableId,
        timeTableOccupancyId = this.timeTableOccupancyId,
        eventVersion = eventVersion,
    )

    private fun <T : AbstractEvent> T.toOutbox() =
        OutBox(eventType = TIME_TABLE_OCCUPIED, eventVersion = eventVersion, payload = this)

    private fun <T : AbstractEvent> createOutbox(event: T): OutBox {
        val outbox = event.toOutbox()
        return repository.save(outbox)
    }
}
