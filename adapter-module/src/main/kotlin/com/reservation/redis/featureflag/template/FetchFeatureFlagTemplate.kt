package com.reservation.redis.featureflag.template

import com.reservation.featureflag.port.output.FindFeatureFlag
import com.reservation.featureflag.port.output.FindFeatureFlag.FindFeatureFlagInquiry
import com.reservation.featureflag.port.output.FindFeatureFlag.FindFeatureFlagResult
import com.reservation.redis.RedisNameSpace
import com.reservation.redis.featureflag.entity.FeatureFlagRedisEntity
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class FetchFeatureFlagTemplate(
    private val featureFlagRedisTemplate: RedisTemplate<String, FeatureFlagRedisEntity>,
) : FindFeatureFlag {
    override fun query(inquiry: FindFeatureFlagInquiry): FindFeatureFlagResult? {
        val opsForValue = featureFlagRedisTemplate.opsForValue()

        return opsForValue.get(inquiry.toKey())?.let { it.toValue() }
    }

    private fun FindFeatureFlagInquiry.toKey(): String =
        "${RedisNameSpace.FEATURE_FLAG}::${this.featureFlagType}:${this.featureFlagKey}"

    private fun FeatureFlagRedisEntity.toValue(): FindFeatureFlagResult =
        FindFeatureFlagResult(
            id = this.id,
            featureFlagType = this.featureFlagType,
            featureFlagKey = this.featureFlagKey,
            isEnabled = this.isEnabled,
        )
}
