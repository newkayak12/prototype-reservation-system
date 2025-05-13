package com.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ReservationApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<ReservationApplication>(*args)
}
