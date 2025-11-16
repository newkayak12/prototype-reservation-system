package com.reservation.persistence.featureflag.entity

import com.reservation.enumeration.FeatureFlagType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

@Table(
    catalog = "prototype_reservation",
    name = "feature_flag",
    indexes = [
        Index(
            columnList = "feature_flag_type, is_enabled",
            unique = false,
            name = "index_feature_flag_type_is_enabled",
        ),
    ],
)
@Entity
class FeatureFlagEntity(
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "identifier")
    private val identifier: Long,
    featureFlagType: FeatureFlagType,
    featureFlagKey: String,
    isEnabled: Boolean,
) {
    @field:Enumerated(STRING)
    @Column(name = "feature_flag_type", nullable = false)
    var featureFlagType: FeatureFlagType = featureFlagType
        protected set

    @Column(name = "feature_flag_key", length = 255, nullable = false)
    var featureFlagKey: String = featureFlagKey
        protected set

    @Column(name = "is_enabled")
    var isEnabled: Boolean = isEnabled
        protected set
}
