package com.reservation.redis.featureflag.template

import com.reservation.exceptions.InvalidRedisStatusException
import com.reservation.featureflag.port.output.SaveFeatureFlag
import com.reservation.featureflag.port.output.SaveFeatureFlag.SaveFeatureFlagInquiry
import com.reservation.redis.RedisNameSpace
import com.reservation.redis.featureflag.entity.FeatureFlagRedisEntity
import io.lettuce.core.RedisException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.MINUTES

@Component
class SaveFeatureFlagTemplate(
    private val featureFlagRedisTemplate: RedisTemplate<String, FeatureFlagRedisEntity>,
) : SaveFeatureFlag {
    companion object {
        private const val DURATION_MINUTES = 360L
    }

    override fun command(inquiry: SaveFeatureFlagInquiry): Boolean {
        try {
            val opsForValue = featureFlagRedisTemplate.opsForValue()
            val key = inquiry.toKey()
            val value = inquiry.toValue()
            return opsForValue.setIfAbsent(key, value, DURATION_MINUTES, MINUTES)
        } catch (_: RedisException) {
            throw InvalidRedisStatusException()
        }
    }

    private fun SaveFeatureFlagInquiry.toKey(): String =
        "${RedisNameSpace.FEATURE_FLAG}::${this.featureFlagType}:${this.featureFlagKey}"

    private fun SaveFeatureFlagInquiry.toValue(): FeatureFlagRedisEntity =
        FeatureFlagRedisEntity(
            id = this.id,
            featureFlagType = this.featureFlagType,
            featureFlagKey = this.featureFlagKey,
            isEnabled = this.isEnabled,
        )
}
