package com.reservation.persistence.user.repository.jpa

import com.reservation.persistence.user.entity.UserAccessHistoryEntity
import org.springframework.data.repository.CrudRepository

interface UserAccessHistoryRepository : CrudRepository<UserAccessHistoryEntity, String>
