package com.reservation.redis.featureflag.entity

import com.reservation.enumeration.FeatureFlagType

data class FeatureFlagRedisEntity(
    val id: Long,
    val featureFlagType: FeatureFlagType,
    val featureFlagKey: String,
    val isEnabled: Boolean,
)
