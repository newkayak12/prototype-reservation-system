package com.reservation.httpinterface.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "http-interface")
data class HttpInterfaceProperties(
    val retry: RetryProperties,
    val internal: InternalProperties,
) {
    data class RetryProperties(
        val maxAttempts: Int = 0,
        val initialInterval: Long = 100L,
        val multiplier: Double = 2.0,
        val maxInterval: Long = 2000L,
    )

    data class InternalProperties(
        val baseUrl: String,
    )
}
