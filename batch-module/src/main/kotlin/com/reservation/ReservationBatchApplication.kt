package com.reservation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ReservationBatchApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<ReservationBatchApplication>(*args)
}
