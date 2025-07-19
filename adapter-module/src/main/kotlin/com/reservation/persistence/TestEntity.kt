package com.reservation.persistence

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class TestEntity : TimeBasedPrimaryKey() {
    @Column
    var testString1: String = ""

    @Column
    var testString2: String = ""
}
