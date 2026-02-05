package com.reservation.redis.redisson.lock.named.adapter

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class AcquireNamedLockRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    companion object {
        const val GET_LOCK_QUERY = "SELECT GET_LOCK(?, 0)"
        const val TRUE = 1
    }

    fun tryAcquireLock(lockName: String): Boolean =
        jdbcTemplate.queryForObject(GET_LOCK_QUERY, Int::class.java, lockName) == TRUE
}
