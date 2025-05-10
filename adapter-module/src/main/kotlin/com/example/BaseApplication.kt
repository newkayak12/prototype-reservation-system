package com.example

import com.example.sample.entity.QSample.sample
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication

class BaseApplication

fun main(args: Array<String>) {
    runApplication<BaseApplication>(*args)
}
