package com.reservation.config.persistence.entity

import com.reservation.utilities.generator.uuid.UuidGenerator
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator

class TimeBasedIdGenerator : IdentifierGenerator {
    override fun generate(
        p0: SharedSessionContractImplementor?,
        p1: Any?,
    ): String {
        return UuidGenerator.generate()
    }
}
