package com.reservation.persistence.restaurant

import com.reservation.persistence.category.entity.CategoryEntity
import com.reservation.persistence.common.AuditDateTime
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

@Table(
    catalog = "prototype_reservation",
    name = "restaurant",
    indexes = [
        Index(columnList = "name", unique = false),
    ],
)
@Entity
@Suppress("LongParameterList")
class RestaurantEntity(
    companyId: String,
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
    var companyId: String = companyId
        protected set

    @Column(name = "name")
    var name: String = name
        protected set

    @Column(name = "introduce")
    var introduce: String = introduce
        protected set

    @Column(name = "phone")
    var phone: String = phone
        protected set

    @Column(name = "zip_code")
    var zipCode: String = zipCode
        protected set

    @Column(name = "address")
    var address: String = address
        protected set

    @Column(name = "detail")
    var detail: String = detail
        protected set

    @Column(name = "latitude", precision = 8, scale = 5)
    var latitude: BigDecimal = latitude
        protected set

    @Column(name = "longitude", precision = 8, scale = 5)
    var longitude: BigDecimal = longitude
        protected set

    @OneToMany(
        mappedBy = "restaurant",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        fetch = FetchType.LAZY,
        orphanRemoval = true,
    )
    private var weekDays = mutableListOf<RestaurantWorkingDayEntity>()

    @ManyToMany(
        targetEntity = CategoryEntity::class,
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
    )
    @JoinTable(
        name = "restaurant_category",
        catalog = "prototype_reservation",
        joinColumns = [
            JoinColumn(
                name = "restaurant_id",
                foreignKey = ForeignKey(NO_CONSTRAINT),
            ),
        ],
        inverseJoinColumns = [
            JoinColumn(
                name = "category_id",
                foreignKey = ForeignKey(NO_CONSTRAINT),
            ),
        ],
    )
    private var categories = mutableListOf<CategoryEntity>()

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

    fun addCategory(category: CategoryEntity) {
        val item = categories.find { it.id == category.id }
        if (item !== null) {
            return
        }
        categories.add(category)
    }

    fun removeCategory(category: CategoryEntity) {
        val item = categories.find { it.id == category.id }
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
}
