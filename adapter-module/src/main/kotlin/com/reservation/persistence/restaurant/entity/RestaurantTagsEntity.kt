package com.reservation.persistence.restaurant.entity

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
    name = "restaurant_tags",
    indexes = [
        Index(
            columnList = "restaurant_id,tags_id",
            unique = false,
            name = "index_restaurant_tags",
        ),
    ],
)
@Entity
class RestaurantTagsEntity(
    @ManyToOne(targetEntity = RestaurantEntity::class)
    @JoinColumn(
        name = "restaurant_id",
        updatable = false,
        foreignKey = ForeignKey(NO_CONSTRAINT),
    )
    @Comment("음식점 식별자")
    private val restaurant: RestaurantEntity,
    @Column(name = "tags_id")
    @Comment("카테고리 식별자")
    val tagsId: Long,
) : TimeBasedPrimaryKey() {
    @Embedded
    var auditDateTime: AuditDateTime = AuditDateTime()
        protected set
}
