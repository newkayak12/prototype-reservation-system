package com.reservation.persistence.featureflag.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.enumeration.FeatureFlagType
import com.reservation.persistence.featureflag.entity.QFeatureFlagEntity.featureFlagEntity

object FeatureFlagQuerySpec {
    fun featureFlagTypeEq(featureFlagType: FeatureFlagType): BooleanExpression =
        featureFlagEntity.featureFlagType.eq(featureFlagType)

    fun featureFlagKeyEq(featureFlagKey: String): BooleanExpression =
        featureFlagEntity.featureFlagKey.eq(featureFlagKey)
}
