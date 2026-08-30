package com.reservation.kafka.config

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

        /** 하류 예약 생성 컨슈머. */
        const val OCCUPIED_CONSUMER = "parallelConsumer"

        /** 좌석 점유 요청 컨슈머. */
        const val OCCUPANCY_REQUEST_CONSUMER = "occupancyRequestParallelConsumer"

        /**
         * 점유 요청 컨슈머만 다른 컨슈머 그룹을 쓴다.
         *
         * 같은 그룹의 멤버들이 서로 다른 토픽을 구독하면 리밸런스마다 마지막에 합류한 멤버의
         * 구독을 기준으로 파티션이 재배정되면서 한쪽이 굶는다. 토픽이 다르면 그룹도 나눈다.
         */
        const val OCCUPANCY_REQUEST_GROUP_SUFFIX = "-occupancy-request"
    }

    private fun resolveProducerBootstrapServers(kafkaProperties: KafkaProperties): List<String>? =
        kafkaProperties.producer.bootstrapServers ?: kafkaProperties.bootstrapServers

    private fun createProducerConfig(kafkaProperties: KafkaProperties): Map<String, Any> {
        val producerConfig = kafkaProperties.producer
        val properties = producerConfig.properties ?: emptyMap()

        val configMap = mutableMapOf<String, Any>()

        // 필수 설정들
        resolveProducerBootstrapServers(kafkaProperties)?.let {
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
    fun kafkaProducerFactory(kafkaProperties: KafkaProperties): ProducerFactory<String, String> {
        val configProps = createProducerConfig(kafkaProperties)
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(
        producerFactory: ProducerFactory<String, String>,
    ): KafkaTemplate<String, String> = KafkaTemplate(producerFactory)

    private fun createConsumerConfig(
        kafkaProperties: KafkaProperties,
        groupIdSuffix: String,
    ): Map<String, Any> {
        val consumerConfig = kafkaProperties.consumer
        val properties = consumerConfig.properties ?: emptyMap()

        val configMap = mutableMapOf<String, Any>()

        // 필수 설정들
        consumerConfig.bootstrapServers?.let {
            configMap[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = it
        }
        consumerConfig.groupId?.let {
            configMap[ConsumerConfig.GROUP_ID_CONFIG] = it + groupIdSuffix
        }
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
        groupIdSuffix: String,
    ): ParallelConsumerOptionsBuilder<String, String> {
        val configProps = createConsumerConfig(kafkaProperties, groupIdSuffix)
        val kafkaConsumer = KafkaConsumer<String, String>(configProps)

        return ParallelConsumerOptions.builder<String, String>()
            .ordering(parallelConsumerProperties.processingOrder) // KEY, PARTITION, UNORDERED
            .maxConcurrency(parallelConsumerProperties.maxConcurrency)
            .consumer(kafkaConsumer) // KafkaConsumer 직접 전달
    }

    private fun createStreamProcessor(
        kafkaProperties: KafkaProperties,
        groupIdSuffix: String,
    ): ParallelStreamProcessor<String, String> {
        val consumer =
            createParallelConsumerOptions(kafkaProperties, groupIdSuffix)
                .shutdownTimeout(Duration.ofSeconds(TEN_SECONDS))
                .build()

        return ParallelStreamProcessor.createEosStreamProcessor(consumer)
    }

    @Bean(OCCUPIED_CONSUMER)
    fun parallelConsumer(
        kafkaProperties: KafkaProperties,
    ): ParallelStreamProcessor<String, String> = createStreamProcessor(kafkaProperties, "")

    /**
     * 좌석 점유 요청 전용 컨슈머.
     *
     * 컨슈머를 하나 더 두는 이유는 [ParallelStreamProcessor]가 `subscribe` + `poll`을 한 번씩만
     * 받는 물건이기 때문이다. 기존 빈을 두 리스너가 나눠 쓰면 나중에 초기화된 쪽이 앞선 구독을
     * 덮어써서 한쪽 토픽이 조용히 소비되지 않는다.
     *
     * `ProcessingOrder.KEY`(설정 파일 기준)를 그대로 물려받는다 — 이 컨슈머에는 그 설정이
     * 편의가 아니라 필수다. 같은 슬롯의 요청이 병렬로 처리되면 둘이 같은 좌석 행을 집는다.
     */
    @Bean(OCCUPANCY_REQUEST_CONSUMER)
    fun occupancyRequestParallelConsumer(
        kafkaProperties: KafkaProperties,
    ): ParallelStreamProcessor<String, String> =
        createStreamProcessor(kafkaProperties, OCCUPANCY_REQUEST_GROUP_SUFFIX)
}
