package com.reservation.config.security.xss.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(value = "security.xss.restricted")
data class XssBlacklist(
    val path: List<String>?,
)
