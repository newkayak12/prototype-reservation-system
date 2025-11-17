package com.reservation.featureflag.port.input.query.response

import com.reservation.enumeration.FeatureFlagType

data class FindFeatureFlagQueryResult(
    val id: Long,
    val featureFlagType: FeatureFlagType,
    val featureFlagKey: String,
    val isEnabled: Boolean,
)
