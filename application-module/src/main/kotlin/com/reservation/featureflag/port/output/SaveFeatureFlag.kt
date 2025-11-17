package com.reservation.featureflag.port.output

import com.reservation.enumeration.FeatureFlagType

interface SaveFeatureFlag {
    fun command(inquiry: SaveFeatureFlagInquiry): Boolean

    data class SaveFeatureFlagInquiry(
        val id: Long,
        val featureFlagType: FeatureFlagType,
        val featureFlagKey: String,
        val isEnabled: Boolean,
    )
}
