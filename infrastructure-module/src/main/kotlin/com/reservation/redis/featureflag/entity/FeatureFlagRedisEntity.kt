package com.reservation.redis.featureflag.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.reservation.enumeration.FeatureFlagType

data class FeatureFlagRedisEntity(
    val id: Long,
    val featureFlagType: FeatureFlagType,
    val featureFlagKey: String,
    @JsonProperty("isEnabled")
    val isEnabled: Boolean,
)
