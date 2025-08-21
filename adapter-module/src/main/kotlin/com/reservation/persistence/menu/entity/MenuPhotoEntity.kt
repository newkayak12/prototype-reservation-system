package com.reservation.persistence.menu.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(
    catalog = "prototype_reservation",
    name = "restaurant_photo",
    indexes = [],
)
@Entity
class MenuPhotoEntity(
    @ManyToOne
    @JoinColumn(
        name = "menu_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(NO_CONSTRAINT),
    )
    @Comment("메뉴 식별키")
    val menu: MenuEntity,
    @Column(name = "url", nullable = false, updatable = false)
    @Comment("URL")
    val url: String,
) : TimeBasedPrimaryKey()
