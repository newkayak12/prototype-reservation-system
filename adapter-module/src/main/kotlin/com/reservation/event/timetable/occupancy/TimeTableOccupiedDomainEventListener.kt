package com.reservation.event.timetable.occupancy

import com.fasterxml.jackson.databind.ObjectMapper
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.enumeration.OutboxEventType
import com.reservation.enumeration.OutboxEventType.TIME_TABLE_OCCUPIED
import com.reservation.event.abstractEvent.AbstractEvent
import com.reservation.kafka.config.KafkaHeader.ORIGINAL_TOPIC_KEY
import com.reservation.kafka.config.KafkaHeader.RETRY_COUNT_KEY
import com.reservation.persistence.outbox.entity.OutBox
import com.reservation.persistence.outbox.repository.OutboxRepository
import com.reservation.timetable.event.TimeTableOccupiedDomainEvent
import org.apache.kafka.clients.producer.ProducerRecord
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
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val repository: OutboxRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        const val TIME_TABLE_OCCUPIED_EVENT_VERSION = 1.0
        const val KAFKA_EVENT_PUBLISH_TIME_OUT = 10L
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun handleCreateTimeTableOccupancyEvent(event: TimeTableOccupiedDomainEvent) {
        val createdEvent =
            event.toKafkaEvent(
                TIME_TABLE_OCCUPIED,
                TIME_TABLE_OCCUPIED_EVENT_VERSION,
            )

        val outbox = createOutbox(createdEvent)
        val nextEvent =
            TimeTableOccupiedOutboxEvent(
                outbox.identifier,
                createdEvent,
            )
        applicationEventPublisher.publishEvent(nextEvent)
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    fun publishKafkaEvent(outboxEvent: TimeTableOccupiedOutboxEvent) {
        val outboxId = outboxEvent.outboxId
        val createdEvent = outboxEvent.event

        val kafkaTopic = createdEvent.eventType.name
        val kafkaKey = createdEvent.key()

        val outbox =
            repository.findById(outboxId)
                .orElseThrow { throw NoSuchPersistedElementException() }

        runCatching {
            val record =
                ProducerRecord(
                    kafkaTopic,
                    kafkaKey,
                    objectMapper.writeValueAsString(createdEvent),
                )

            record.headers().add(RETRY_COUNT_KEY, "0".toByteArray(Charsets.UTF_8))
            record.headers().add(ORIGINAL_TOPIC_KEY, kafkaTopic.toByteArray(Charsets.UTF_8))

            kafkaTemplate.send(record)
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
