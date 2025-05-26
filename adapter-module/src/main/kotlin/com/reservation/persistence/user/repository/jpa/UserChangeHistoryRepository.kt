package com.reservation.persistence.user.repository.jpa

import com.reservation.persistence.user.entity.UserChangeHistoryEntity
import org.springframework.data.repository.CrudRepository

interface UserChangeHistoryRepository : CrudRepository<UserChangeHistoryEntity, String>
