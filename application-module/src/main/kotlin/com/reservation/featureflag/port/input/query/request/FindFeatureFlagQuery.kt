package com.reservation.featureflag.port.input.query.request

import com.reservation.enumeration.FeatureFlagType

data class FindFeatureFlagQuery(
    val featureFlagType: FeatureFlagType,
    val featureFlagKey: String,
)
