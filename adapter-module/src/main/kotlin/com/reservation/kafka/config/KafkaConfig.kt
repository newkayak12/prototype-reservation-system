package com.reservation.kafka.config

import com.reservation.event.abstractEvent.AbstractEvent
import io.confluent.parallelconsumer.ParallelConsumerOptions
import io.confluent.parallelconsumer.ParallelStreamProcessor
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
@EnableKafka
class KafkaConfig(
    private val parallelConsumerProperties: KafkaParallelConsumerProperties,
) {
    companion object {
        const val LINGER_MS = "linger.ms"
        const val ENABLE_IDEMPOTENCE = "enable.idempotence"
        const val MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION = "max.in.flight.requests.per.connection"
        const val DELIVERY_TIMEOUT_MS = "delivery.timeout.ms"
        const val REQUEST_TIMEOUT_MS = "request.timeout.ms"

        const val ISOLATION_LEVEL = "isolation.level"
        const val SESSION_TIMEOUT_MS = "session.timeout.ms"
        const val HEARTBEAT_INTERVAL_MS = "heartbeat.interval.ms"
        const val MAX_POLL_RECORDS = "max.poll.records"
        const val FETCH_MIN_BYTES = "fetch.min.bytes"
        const val FETCH_MAX_WAIT_MS = "fetch.max.wait.ms"
    }

    private fun createProducerConfig(kafkaProperties: KafkaProperties): Map<String, Any?> {
        val producerConfig = kafkaProperties.producer
        val properties = producerConfig.properties
        return mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to producerConfig.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to producerConfig.keySerializer,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to producerConfig.valueSerializer,
            ProducerConfig.ACKS_CONFIG to producerConfig.acks,
            ProducerConfig.RETRIES_CONFIG to producerConfig.retries,
            ProducerConfig.BUFFER_MEMORY_CONFIG to producerConfig.bufferMemory,
            ProducerConfig.COMPRESSION_TYPE_CONFIG to producerConfig.compressionType,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to
                properties[MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION],
            ProducerConfig.LINGER_MS_CONFIG to properties[LINGER_MS],
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to properties[ENABLE_IDEMPOTENCE],
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to properties[DELIVERY_TIMEOUT_MS],
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to properties[REQUEST_TIMEOUT_MS],
        )
    }

    @Bean
    fun kafkaProducerFactory(
        kafkaProperties: KafkaProperties,
    ): ProducerFactory<String, AbstractEvent> {
        val configProps = createProducerConfig(kafkaProperties)
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(
        producerFactory: ProducerFactory<String, AbstractEvent>,
    ): KafkaTemplate<String, AbstractEvent> = KafkaTemplate(producerFactory)

    private fun createConsumerConfig(kafkaProperties: KafkaProperties): Map<String, Any?> {
        val consumerConfig = kafkaProperties.consumer
        val properties = consumerConfig.properties

        return mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to consumerConfig.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to consumerConfig.groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to consumerConfig.autoOffsetReset,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to consumerConfig.enableAutoCommit,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to consumerConfig.keyDeserializer,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to consumerConfig.valueDeserializer,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG to properties[ISOLATION_LEVEL],
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to properties[SESSION_TIMEOUT_MS],
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to properties[HEARTBEAT_INTERVAL_MS],
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to properties[MAX_POLL_RECORDS],
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG to properties[FETCH_MIN_BYTES],
            ConsumerConfig.FETCH_MAX_BYTES_CONFIG to properties[FETCH_MAX_WAIT_MS],
        )
    }

    @Bean
    fun parallelConsumerOptions(
        kafkaProperties: KafkaProperties,
    ): ParallelConsumerOptions<String, Any> {
        val configProps = createConsumerConfig(kafkaProperties)
        val kafkaConsumer = KafkaConsumer<String, Any>(configProps)

        return ParallelConsumerOptions.builder<String, Any>()
            .ordering(parallelConsumerProperties.processingOrder) // KEY, PARTITION, UNORDERED
            .maxConcurrency(parallelConsumerProperties.maxConcurrency)
            .consumer(kafkaConsumer) // KafkaConsumer 직접 전달
            .build()
    }

    @Bean
    fun parallelEasyConsumer(
        options: ParallelConsumerOptions<String, Any>,
    ): ParallelStreamProcessor<String, Any> =
        ParallelStreamProcessor.createEosStreamProcessor(options)
}
