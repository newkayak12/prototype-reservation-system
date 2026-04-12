package com.reservation.persistence.reservation.repository.jpa

import com.reservation.persistence.reservation.entity.ReservationEntity
import org.springframework.data.repository.CrudRepository

interface ReservationJpaRepository : CrudRepository<ReservationEntity, String>
