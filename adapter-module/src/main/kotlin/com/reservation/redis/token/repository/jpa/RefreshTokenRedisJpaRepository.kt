package com.reservation.redis.token.repository.jpa

import com.reservation.redis.token.entity.RefreshTokenRedisEntity
import org.springframework.data.repository.CrudRepository

interface RefreshTokenRedisJpaRepository : CrudRepository<RefreshTokenRedisEntity, String>
