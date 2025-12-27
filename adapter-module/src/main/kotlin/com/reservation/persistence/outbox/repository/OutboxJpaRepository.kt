package com.reservation.persistence.outbox.repository

import com.reservation.persistence.outbox.entity.OutBox
import org.springframework.data.repository.CrudRepository

interface OutboxJpaRepository : CrudRepository<OutBox, Long>
