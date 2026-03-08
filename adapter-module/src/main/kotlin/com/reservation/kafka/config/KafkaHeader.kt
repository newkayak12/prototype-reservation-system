package com.reservation.kafka.config

object KafkaHeader {
    const val RETRY_COUNT_KEY = "x-retry-count"
    const val ORIGINAL_TOPIC_KEY = "x-original-topic"
    const val ERROR_REASON_KEY = "x-error-reason"
    const val FAILED_TIMESTAMP_KEY = "x-failed-timestamp"
}
