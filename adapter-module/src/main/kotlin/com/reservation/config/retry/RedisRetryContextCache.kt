package com.reservation.config.retry

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.retry.RetryContext
import org.springframework.retry.policy.RetryContextCache
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisRetryContextCache(
    private val redisRetryContextCacheTemplate: RedisTemplate<String, SerializableRetryContext>,
) : RetryContextCache {
    companion object {
        private const val KEY_PREFIX = "retry:"
        private val TTL = Duration.ofDays(7)
    }

    override fun get(key: Any): RetryContext {
        val redisKey = "$KEY_PREFIX$key"
        return redisRetryContextCacheTemplate.opsForValue().get(redisKey)
    }

    override fun put(
        key: Any,
        context: RetryContext,
    ) {
        val redisKey = "$KEY_PREFIX$key"

        val serializable =
            if (context is SerializableRetryContext) {
                context
            } else {
                SerializableRetryContext(
                    retryCount = context.retryCount,
                    lastExceptionType = context.lastThrowable?.javaClass?.name,
                    lastExceptionMessage = context.lastThrowable?.message,
                    startTime =
                        context.getAttribute("startTime") as? Long
                            ?: System.currentTimeMillis(),
                )
            }

        redisRetryContextCacheTemplate.opsForValue().set(redisKey, serializable, TTL)
    }

    override fun remove(key: Any) {
        val redisKey = "$KEY_PREFIX$key"
        redisRetryContextCacheTemplate.delete(redisKey)
    }

    override fun containsKey(key: Any): Boolean {
        val redisKey = "$KEY_PREFIX$key"
        return redisRetryContextCacheTemplate.hasKey(redisKey)
    }
}
