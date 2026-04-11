package com.reservation.persistence.featureflag.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.featureflag.port.output.FindFeatureFlag
import com.reservation.featureflag.port.output.FindFeatureFlag.FindFeatureFlagInquiry
import com.reservation.featureflag.port.output.FindFeatureFlag.FindFeatureFlagResult
import com.reservation.persistence.featureflag.entity.QFeatureFlagEntity.featureFlagEntity
import org.springframework.stereotype.Component

@Component
class FindFeatureFlagRepository(
    private val query: JPAQueryFactory,
) : FindFeatureFlag {
    override fun query(inquiry: FindFeatureFlagInquiry): FindFeatureFlagResult? {
        return query
            .select(
                Projections.constructor(
                    FindFeatureFlagResult::class.java,
                    featureFlagEntity.identifier,
                    featureFlagEntity.featureFlagType,
                    featureFlagEntity.featureFlagKey,
                    featureFlagEntity.isEnabled,
                ),
            )
            .from(featureFlagEntity)
            .where(
                FeatureFlagQuerySpec.featureFlagTypeEq(inquiry.featureFlagType),
                FeatureFlagQuerySpec.featureFlagKeyEq(inquiry.featureFlagKey),
            )
            .fetchOne()
    }
}
