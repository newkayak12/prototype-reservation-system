package com.example.generator.uuid

import com.fasterxml.uuid.Generators

object UuidGenerator {
    fun generate(prefix: String): String = StringBuilder().apply {
            append(prefix)
            append(Generators.timeBasedEpochGenerator().generate().toString())
        }.toString()

    fun generate(): String = Generators.timeBasedEpochGenerator().generate().toString()
}