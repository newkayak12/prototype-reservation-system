package com.reservation.redis.config

import com.reservation.redis.featureflag.entity.FeatureFlagRedisEntity
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisTemplateConfig {
    @Bean
    fun redisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
    ): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = redisConnectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = GenericJackson2JsonRedisSerializer()

        return template
    }

    @Bean
    fun featureFlagRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
    ): RedisTemplate<String, FeatureFlagRedisEntity> {
        val template = RedisTemplate<String, FeatureFlagRedisEntity>()
        template.connectionFactory = redisConnectionFactory
        template.keySerializer = StringRedisSerializer()
        val jsonSerializer = Jackson2JsonRedisSerializer(FeatureFlagRedisEntity::class.java)
        template.valueSerializer = jsonSerializer
        template.hashValueSerializer = jsonSerializer

        return template
    }
}
