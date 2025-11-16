package com.reservation.featureflag.port.output

import com.reservation.enumeration.FeatureFlagType

interface FindFeatureFlag {
    fun query(inquiry: FindFeatureFlagInquiry): FindFeatureFlagResult?

    data class FindFeatureFlagInquiry(
        val userId: String,
        val featureFlagType: FeatureFlagType,
        val featureFlagKey: String,
    )

    data class FindFeatureFlagResult(
        val id: Long,
        val featureFlagType: FeatureFlagType,
        val featureFlagKey: String,
        val isEnabled: Boolean,
    )
}
