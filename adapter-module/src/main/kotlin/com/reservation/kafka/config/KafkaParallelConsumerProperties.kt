package com.reservation.kafka.config

import io.confluent.parallelconsumer.ParallelConsumerOptions
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.kafka.consumer.parallel")
data class KafkaParallelConsumerProperties(
    val processingOrder: ParallelConsumerOptions.ProcessingOrder,
    val maxConcurrency: Int,
)
