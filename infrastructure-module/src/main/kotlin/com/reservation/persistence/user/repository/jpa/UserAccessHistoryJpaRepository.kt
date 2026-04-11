package com.reservation.persistence.user.repository.jpa

import com.reservation.persistence.user.entity.UserAccessHistoryEntity
import org.springframework.data.repository.CrudRepository

interface UserAccessHistoryJpaRepository : CrudRepository<UserAccessHistoryEntity, String>
