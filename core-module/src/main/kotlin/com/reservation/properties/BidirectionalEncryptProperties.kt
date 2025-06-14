package com.reservation.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(value = "key.bidirectional")
class BidirectionalEncryptProperties(
    val secret: String,
)
