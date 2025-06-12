package com.reservation.utilities.generator.uuid

import com.fasterxml.uuid.Generators

object UuidGenerator {
    fun generate(prefix: String): String =
        StringBuilder().apply {
            append(prefix)
            append(Generators.timeBasedEpochGenerator().generate().toString())
        }.toString()

    fun generate(): String = Generators.timeBasedEpochGenerator().generate().toString()
}
