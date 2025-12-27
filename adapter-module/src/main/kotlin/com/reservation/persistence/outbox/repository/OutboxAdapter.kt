package com.reservation.persistence.outbox.repository

import com.reservation.persistence.outbox.entity.OutBox
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class OutboxAdapter(
    private val jpaRepository: OutboxJpaRepository,
) : OutboxRepository {
    override fun save(entity: OutBox): OutBox = jpaRepository.save(entity)

    override fun findById(id: Long): Optional<OutBox> = jpaRepository.findById(id)
}
