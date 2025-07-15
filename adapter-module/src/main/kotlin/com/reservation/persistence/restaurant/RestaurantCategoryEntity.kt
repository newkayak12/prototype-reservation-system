package com.reservation.persistence.restaurant

import com.reservation.persistence.common.AuditDateTime
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Table(
    catalog = "prototype_reservation",
    name = "restaurant_category",
    indexes = [
        Index(columnList = "name is_deleted", unique = false),
    ],
)
@Entity
class RestaurantCategoryEntity(
    @ManyToOne(targetEntity = RestaurantEntity::class)
    @JoinColumn(
        name = "restaurant_id",
        updatable = false,
        foreignKey = ForeignKey(NO_CONSTRAINT),
    )
    private val restaurant: RestaurantEntity,
    val categoryId: Long,
) : TimeBasedPrimaryKey() {
    @Embedded
    var auditDateTime: AuditDateTime = AuditDateTime()
        protected set
}
