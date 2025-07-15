package com.reservation.persistence.restaurant

import com.reservation.persistence.common.AuditDateTime
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(
    catalog = "prototype_reservation",
    name = "restaurant_category",
    indexes = [
        Index(
            columnList = "restaurant_id category_id",
            unique = false,
            name = "index_restaurant_category",
        ),
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
    @Comment("음식점 식별자")
    private val restaurant: RestaurantEntity,
    @Column(name = "category_id")
    @Comment("카테고리 식별자")
    val categoryId: Long,
) : TimeBasedPrimaryKey() {
    @Embedded
    var auditDateTime: AuditDateTime = AuditDateTime()
        protected set
}
