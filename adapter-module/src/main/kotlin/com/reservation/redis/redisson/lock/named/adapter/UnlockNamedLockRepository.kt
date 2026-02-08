package com.reservation.redis.redisson.lock.named.adapter

import com.reservation.redis.redisson.lock.named.adapter.AcquireNamedLockRepository.Companion.TRUE
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class UnlockNamedLockRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    companion object {
        const val RELEASE_LOCK_QUERY = "SELECT RELEASE_LOCK(?)"
    }

    fun releaseLock(lockName: String): Boolean =
        jdbcTemplate.queryForObject(RELEASE_LOCK_QUERY, Int::class.java, lockName) == TRUE
}
