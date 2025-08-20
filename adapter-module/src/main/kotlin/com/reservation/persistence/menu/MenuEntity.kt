package com.reservation.persistence.menu

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Filter
import org.hibernate.annotations.FilterDef
import java.math.BigDecimal

@Table(
    catalog = "prototype_reservation",
    name = "menu",
    indexes = [
        Index(columnList = "title, is_deleted", unique = false, name = "index_title_is_deleted"),
    ],
)
@Entity
@DynamicUpdate
@Suppress("LongParameterList", "TooManyFunctions")
@FilterDef(name = "is_not_deleted", defaultCondition = "is_deleted = false")
@Filter(name = "is_not_deleted")
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
    private val photos = mutableListOf<MenuPhotoEntity>()

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

    fun adjustPrice(price: BigDecimal) {
        if (price < BigDecimal.ZERO) return
        this.price = price
    }

    fun adjustPhotos(block: MutableList<MenuPhotoEntity>.() -> Unit) = photos.apply(block)
}
