package com.reservation.persistence.withdrawal.repository.jpa

import com.reservation.persistence.withdrawal.entity.WithdrawalUserEntity
import org.springframework.data.repository.CrudRepository

interface WithdrawalJpaRepository : CrudRepository<WithdrawalUserEntity, String>
