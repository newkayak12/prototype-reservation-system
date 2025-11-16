package com.reservation.featureflag.port.output

import com.reservation.enumeration.FeatureFlagType

interface SaveFeatureFlag {

    fun command(inquiry: SaveFeatureFlagInquiry): Boolean

    data class SaveFeatureFlagInquiry(
        val userId: String,
        val featureFlagType: FeatureFlagType,
        val featureFlagKey: String,
        val isEnabled: Boolean,
    )
}
