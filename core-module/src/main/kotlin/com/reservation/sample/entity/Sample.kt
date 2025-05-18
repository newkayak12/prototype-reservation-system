package com.reservation.sample.entity

import com.reservation.sample.config.TimeBasedUuidStrategy
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Table(name = "sample")
@Entity
class Sample {
    @Id
    @TimeBasedUuidStrategy
    val id: String? = null

    @Column
    lateinit var address: String
        private set
}
