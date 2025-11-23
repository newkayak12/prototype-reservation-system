package com.reservation.redis.redisson.timetable.lock.fair

import com.reservation.timetable.port.output.AcquireTimeTableFairLock
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class AcquireFairLockTemplate(
    private val redissonClient: RedissonClient,
) : AcquireTimeTableFairLock {
    override fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean {
        val lock =
            FairLockStore.getOrCreateFairLock(name) {
                redissonClient.getFairLock(FairLockStore.key(name))
            }
        return lock.tryLock(waitTime, waitTimeUnit)
    }
}
