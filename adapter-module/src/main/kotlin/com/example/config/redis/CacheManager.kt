package com.example.config.redis

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import kotlin.reflect.KClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
internal class CacheManager(val objectMapper: ObjectMapper) {
    @Bean
    fun redisCacheManager(redisConnectionFactory: RedisConnectionFactory): RedisCacheManager =
        RedisCacheManagerBuilder
            .fromConnectionFactory(redisConnectionFactory)
            .cacheDefaults(defaultConfiguration(Any::class, Duration.ofDays(1)))
            .withInitialCacheConfigurations(initialCacheConfigurations())
            .build()

    private inline fun <reified T : Any> defaultConfiguration(
        clazz: KClass<T>,
        ttl: Duration,
    ): RedisCacheConfiguration =
        RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()),
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    Jackson2JsonRedisSerializer(clazz::class.java),
                ),
            )
            .entryTtl(ttl)

    @Suppress("UnusedPrivateMember")
    private inline fun <reified T : Any> listConfiguration(
        clazz: KClass<T>,
        ttl: Duration,
    ): RedisCacheConfiguration {
        val typeFactory: TypeFactory = objectMapper.typeFactory
        val javaType: JavaType =
            typeFactory.constructCollectionType(
                MutableList::class.java,
                clazz::class.java,
            )
        val serializer = Jackson2JsonRedisSerializer<List<T>>(javaType)

        return RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()),
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer),
            )
            .entryTtl(ttl)
    }

    private fun initialCacheConfigurations(): Map<String, RedisCacheConfiguration> =
        mutableMapOf<String, RedisCacheConfiguration>()
}
