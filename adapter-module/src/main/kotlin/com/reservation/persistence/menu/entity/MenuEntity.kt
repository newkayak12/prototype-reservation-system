package com.reservation.persistence.menu.entity

import com.reservation.persistence.common.LogicalDelete
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal

@Table(
    catalog = "prototype_reservation",
    name = "menu",
    indexes = [
        Index(columnList = "title, is_deleted", unique = false, name = "index_title_is_deleted"),
        Index(columnList = "restaurant_id", unique = false, name = "index_restaurant_id"),
    ],
)
@Entity
@DynamicUpdate
@Suppress("LongParameterList", "TooManyFunctions")
@SQLRestriction(value = "is_deleted = false")
class MenuEntity(
    restaurantId: String,
    title: String,
    description: String,
    price: BigDecimal,
) : TimeBasedPrimaryKey() {
    @Column(name = "restaurant_id")
    @Comment("restaurant 식별 키")
    val restaurantId: String = restaurantId

    @Column(name = "title", length = 30)
    @Comment("이름")
    var title: String = title
        protected set

    @Column(name = "description", length = 255)
    @Comment("설명")
    var description: String = description
        protected set

    @Column(name = "price", scale = 10, precision = 0)
    @Comment("가격")
    var price: BigDecimal = price
        protected set

    @Column(name = "is_representative")
    @Comment("대표 메뉴 여부")
    var isRepresentative: Boolean = false
        protected set

    @Column(name = "is_recommended")
    @Comment("추천 메뉴 여부")
    var isRecommended: Boolean = false
        protected set

    @Column(name = "is_visible")
    @Comment("노출 여부 ")
    var isVisible: Boolean = false
        protected set

    @OneToMany(
        mappedBy = "menu",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        targetEntity = MenuPhotoEntity::class,
        orphanRemoval = true,
    )
    val photos = mutableListOf<MenuPhotoEntity>()

    @Embedded
    var logicalDelete: LogicalDelete = LogicalDelete()
        protected set

    fun updateTitle(title: String) {
        this.title = title
    }

    fun updateDescription(description: String) {
        this.description = description
    }

    fun updatePrice(price: BigDecimal) {
        if (price < BigDecimal.ZERO) return
        this.price = price
    }

    fun adjustPhotos(block: MutableList<MenuPhotoEntity>.() -> Unit) = photos.apply(block)

    private fun Boolean.toggle(): Boolean = !this

    fun toggleRepresentative() {
        isRepresentative = isRepresentative.toggle()
    }

    fun toggleRecommended() {
        isRecommended = isRecommended.toggle()
    }

    fun toggleVisible() {
        isVisible = isVisible.toggle()
    }
}
