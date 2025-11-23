package com.reservation.redis.redisson.timetable.lock.fair

import com.reservation.timetable.port.output.UnlockTimeTableFairLock
import org.springframework.stereotype.Component

@Component
class UnlockFairLockTemplate : UnlockTimeTableFairLock {
    override fun unlock(name: String) {
        val lock = FairLockStore.getFairLock(name)

        lock.unlock()
    }
}
