package com.example.config

import com.example.config.security.jwt.properties.JwtProperties
import com.example.config.security.jwt.properties.JwtWhitelist
import com.example.config.security.xss.properties.XssBlacklist
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(value = [JwtProperties::class, JwtWhitelist::class, XssBlacklist::class])
class PropertiesConfig {
}