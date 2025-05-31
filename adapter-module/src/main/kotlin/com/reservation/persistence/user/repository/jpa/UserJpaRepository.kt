package com.reservation.persistence.user.repository.jpa

import com.reservation.persistence.user.entity.UserEntity
import org.springframework.data.repository.CrudRepository

interface UserJpaRepository : CrudRepository<UserEntity, String>
