package com.example.sample.entity

import com.example.sample.config.TimeBasedUuidStrategy
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