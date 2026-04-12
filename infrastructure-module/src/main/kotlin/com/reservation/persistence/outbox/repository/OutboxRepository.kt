package com.reservation.persistence.outbox.repository

import com.reservation.persistence.outbox.entity.OutBox
import java.util.Optional

interface OutboxRepository {
    fun save(entity: OutBox): OutBox

    fun findById(id: Long): Optional<OutBox>
}
