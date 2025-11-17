package com.reservation.config.annotations

import com.reservation.enumeration.FeatureFlagType
import com.reservation.enumeration.FeatureFlagType.BACKEND
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class FeatureFlag(
    val featureFlagType: FeatureFlagType = BACKEND,
    val featureFlagKey: String,
    val fallback: String = "",
)
