package com.reservation.kafka.config

import com.reservation.event.abstractEvent.AbstractEvent
import io.confluent.parallelconsumer.ParallelConsumerOptions
import io.confluent.parallelconsumer.ParallelConsumerOptions.ParallelConsumerOptionsBuilder
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
import java.time.Duration

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
        const val TEN_SECONDS = 10L
    }

    private fun createProducerConfig(kafkaProperties: KafkaProperties): Map<String, Any> {
        val producerConfig = kafkaProperties.producer
        val properties = producerConfig.properties ?: emptyMap()

        val configMap = mutableMapOf<String, Any>()

        // 필수 설정들
        producerConfig.bootstrapServers?.let {
            configMap[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = it
        }
        producerConfig.keySerializer?.let {
            configMap[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = it
        }
        producerConfig.valueSerializer?.let {
            configMap[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = it
        }
        producerConfig.acks?.let { configMap[ProducerConfig.ACKS_CONFIG] = it }
        producerConfig.retries?.let { configMap[ProducerConfig.RETRIES_CONFIG] = it }
        producerConfig.bufferMemory?.let {
            configMap[ProducerConfig.BUFFER_MEMORY_CONFIG] = it.toBytes()
        }
        producerConfig.compressionType?.let {
            configMap[ProducerConfig.COMPRESSION_TYPE_CONFIG] = it
        }

        // 선택적 설정들 (null이 아닌 경우만 추가)
        properties[MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION]?.let {
            configMap[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = it
        }
        properties[LINGER_MS]?.let { configMap[ProducerConfig.LINGER_MS_CONFIG] = it }
        properties[ENABLE_IDEMPOTENCE]?.let {
            configMap[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = it
        }
        properties[DELIVERY_TIMEOUT_MS]?.let {
            configMap[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = it
        }
        properties[REQUEST_TIMEOUT_MS]?.let {
            configMap[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = it
        }

        return configMap
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

    private fun createConsumerConfig(kafkaProperties: KafkaProperties): Map<String, Any> {
        val consumerConfig = kafkaProperties.consumer
        val properties = consumerConfig.properties ?: emptyMap()

        val configMap = mutableMapOf<String, Any>()

        // 필수 설정들
        consumerConfig.bootstrapServers?.let {
            configMap[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = it
        }
        consumerConfig.groupId?.let { configMap[ConsumerConfig.GROUP_ID_CONFIG] = it }
        consumerConfig.autoOffsetReset?.let {
            configMap[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = it
        }
        consumerConfig.enableAutoCommit?.let {
            configMap[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = it
        }
        consumerConfig.keyDeserializer?.let {
            configMap[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = it
        }
        consumerConfig.valueDeserializer?.let {
            configMap[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = it
        }

        // 선택적 설정들 (null이 아닌 경우만 추가)
        properties[ISOLATION_LEVEL]?.let { configMap[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = it }
        properties[SESSION_TIMEOUT_MS]?.let {
            configMap[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = it
        }
        properties[HEARTBEAT_INTERVAL_MS]?.let {
            configMap[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = it
        }
        properties[MAX_POLL_RECORDS]?.let { configMap[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = it }
        properties[FETCH_MIN_BYTES]?.let { configMap[ConsumerConfig.FETCH_MIN_BYTES_CONFIG] = it }
        properties[FETCH_MAX_WAIT_MS]?.let { configMap[ConsumerConfig.FETCH_MAX_BYTES_CONFIG] = it }

        return configMap
    }

    private fun createParallelConsumerOptions(
        kafkaProperties: KafkaProperties,
    ): ParallelConsumerOptionsBuilder<String, String> {
        val configProps = createConsumerConfig(kafkaProperties)
        val kafkaConsumer = KafkaConsumer<String, String>(configProps)

        return ParallelConsumerOptions.builder<String, String>()
            .ordering(parallelConsumerProperties.processingOrder) // KEY, PARTITION, UNORDERED
            .maxConcurrency(parallelConsumerProperties.maxConcurrency)
            .consumer(kafkaConsumer) // KafkaConsumer 직접 전달
    }

    @Bean
    fun parallelConsumer(
        kafkaProperties: KafkaProperties,
    ): ParallelStreamProcessor<String, String> {
        val consumer =
            createParallelConsumerOptions(kafkaProperties)
                .shutdownTimeout(Duration.ofSeconds(TEN_SECONDS))
                .build()

        return ParallelStreamProcessor.createEosStreamProcessor(consumer)
    }
}
