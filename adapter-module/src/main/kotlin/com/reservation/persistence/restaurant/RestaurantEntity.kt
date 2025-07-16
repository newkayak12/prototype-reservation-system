package com.reservation.persistence.restaurant

import com.reservation.persistence.common.AuditDateTime
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
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

@Table(
    catalog = "prototype_reservation",
    name = "restaurant",
    indexes = [
        Index(columnList = "name is_deleted", unique = false, name = "index_name_is_deleted"),
    ],
)
@Entity
@Suppress("LongParameterList")
class RestaurantEntity(
    companyId: String,
    userId: String,
    name: String,
    introduce: String,
    phone: String,
    zipCode: String,
    address: String,
    detail: String,
    latitude: BigDecimal,
    longitude: BigDecimal,
) : TimeBasedPrimaryKey() {
    @Column(name = "company_id")
    @Comment("company 식별키")
    var companyId: String = companyId
        protected set

    @Column(name = "user_id")
    @Comment("user 식별키")
    var userId: String = userId
        protected set

    @Column(name = "name")
    @Comment("음식점 이름")
    var name: String = name
        protected set

    @Column(name = "introduce")
    @Comment("음식점 소개")
    var introduce: String = introduce
        protected set

    @Column(name = "phone")
    @Comment("음식점 전화번호")
    var phone: String = phone
        protected set

    @Column(name = "zip_code")
    @Comment("우편번호")
    var zipCode: String = zipCode
        protected set

    @Column(name = "address")
    @Comment("음식점 주소")
    var address: String = address
        protected set

    @Column(name = "detail")
    @Comment("음식점 주소 상세")
    var detail: String = detail
        protected set

    @Column(name = "latitude", precision = 8, scale = 5)
    @Comment("위도")
    var latitude: BigDecimal = latitude
        protected set

    @Column(name = "longitude", precision = 8, scale = 5)
    @Comment("경도")
    var longitude: BigDecimal = longitude
        protected set

    @OneToMany(
        mappedBy = "restaurant",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        fetch = FetchType.LAZY,
        orphanRemoval = true,
    )
    private var weekDays = mutableListOf<RestaurantWorkingDayEntity>()

    @OneToMany(
        mappedBy = "restaurant",
        targetEntity = RestaurantCategoryEntity::class,
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
    )
    private var categories = mutableListOf<RestaurantCategoryEntity>()

    @OneToMany(
        mappedBy = "restaurant",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        targetEntity = RestaurantPhotoEntity::class,
        orphanRemoval = true,
    )
    private var photos = mutableListOf<RestaurantPhotoEntity>()

    @Embedded
    var auditDateTime: AuditDateTime = AuditDateTime()
        protected set

    @Embedded
    var logicalDelete: LogicalDelete = LogicalDelete()
        protected set

    fun weekDaysAll() = weekDays.toList()

    fun categoriesAll() = categories.toList()

    fun photosAll() = photos.toList()

    fun addWeekDay(
        day: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime,
    ) {
        val item = weekDays.find { it.id.day == day }
        if (item != null) {
            return
        }
        weekDays.add(
            RestaurantWorkingDayEntity(
                id = RestaurantWorkingDayId(this.identifier, day),
                restaurant = this,
                startTime = startTime,
                endTime = endTime,
            ),
        )
    }

    fun removeWeekDay(day: DayOfWeek) {
        val item = weekDays.find { it.id.day == day }
        if (item == null) {
            return
        }
        weekDays.remove(item)
    }

    fun addCategory(categoryId: Long) {
        val item = categories.find { it.categoryId == categoryId }
        if (item != null) {
            return
        }
        categories.add(
            RestaurantCategoryEntity(
                restaurant = this,
                categoryId = categoryId,
            ),
        )
    }

    fun removeCategory(categoryId: Long) {
        val item = categories.find { it.categoryId == categoryId }
        if (item == null) {
            return
        }
        categories.remove(item)
    }

    fun addPhoto(url: String) {
        val item = photos.find { it.url == url }
        if (item != null) {
            return
        }

        photos.add(RestaurantPhotoEntity(this, url))
    }

    fun removePhoto(url: String) {
        val item = photos.find { it.url == url }
        if (item == null) {
            return
        }

        photos.remove(item)
    }

    fun delete() {
        logicalDelete = LogicalDelete(true)
    }
}
