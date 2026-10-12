package com.reservation.persistence.schedule.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import com.reservation.persistence.converter.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Table(
    catalog = "prototype_reservation",
    name = "table",
    indexes = [],
)
@Entity
class TableEntity(
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "restaurant_id", columnDefinition = "BINARY(16)")
    val restaurantId: String,
    tableNumber: Int,
    tableSize: Int,
) : TimeBasedPrimaryKey() {
    @Column(name = "table_number")
    var tableNumber: Int = tableNumber
        protected set

    @Column(name = "table_size")
    var tableSize: Int = tableSize
        protected set
}
