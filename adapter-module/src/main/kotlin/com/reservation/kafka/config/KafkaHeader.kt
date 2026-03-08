package com.reservation.kafka.config

object KafkaHeader {
    const val RETRY_COUNT_KEY = "x-retry-count"
    const val RETRY_INTERVAL = 1000
    const val ORIGINAL_TOPIC_KEY = "x-original-topic"
}
