package com.reservation.persistence.restaurant.entity

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
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal

@Table(
    catalog = "prototype_reservation",
    name = "restaurant",
    indexes = [
        Index(columnList = "name is_deleted", unique = false, name = "index_name_is_deleted"),
    ],
)
@Entity
@DynamicUpdate
@Suppress("LongParameterList", "TooManyFunctions")
@SQLRestriction(value = "is_deleted = false")
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
        internal set

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
    private var workingDays = mutableListOf<RestaurantWorkingDayEntity>()

    @OneToMany(
        mappedBy = "restaurant",
        targetEntity = RestaurantTagsEntity::class,
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        orphanRemoval = true,
    )
    private var tags = mutableListOf<RestaurantTagsEntity>()

    @OneToMany(
        mappedBy = "restaurant",
        targetEntity = RestaurantNationalitiesEntity::class,
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        orphanRemoval = true,
    )
    private var nationalities = mutableListOf<RestaurantNationalitiesEntity>()

    @OneToMany(
        mappedBy = "restaurant",
        targetEntity = RestaurantCuisinesEntity::class,
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        orphanRemoval = true,
    )
    private var cuisines = mutableListOf<RestaurantCuisinesEntity>()

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

    fun workingDaysAll() = workingDays.toList()

    fun tagsAll() = tags.toList()

    fun nationalitiesAll() = nationalities.toList()

    fun cuisinesAll() = cuisines.toList()

    fun photosAll() = photos.toList()

    fun adjustWorkingDay(block: MutableList<RestaurantWorkingDayEntity>.() -> Unit) =
        workingDays.apply(block)

    fun adjustTag(block: MutableList<RestaurantTagsEntity>.() -> Unit) = tags.apply(block)

    fun adjustNationalities(block: MutableList<RestaurantNationalitiesEntity>.() -> Unit) =
        nationalities.apply(block)

    fun adjustCuisines(block: MutableList<RestaurantCuisinesEntity>.() -> Unit) =
        cuisines.apply(block)

    fun adjustPhotos(block: MutableList<RestaurantPhotoEntity>.() -> Unit) = photos.apply(block)

    fun delete() {
        logicalDelete = LogicalDelete(true)
    }

    fun updateDescription(
        name: String,
        introduce: String,
    ) {
        this.name = name
        this.introduce = introduce
    }

    fun updateContact(phone: String) {
        this.phone = phone
    }

    fun updateAddress(
        zipCode: String,
        address: String,
        detail: String,
        latitude: BigDecimal,
        longitude: BigDecimal,
    ) {
        this.zipCode = zipCode
        this.address = address
        this.detail = detail
        this.latitude = latitude
        this.longitude = longitude
    }
}
