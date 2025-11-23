package com.reservation.redis.redisson.timetable.lock.fair

import com.reservation.timetable.port.output.CheckTimeTableFairLock
import org.springframework.stereotype.Component

@Component
class CheckFairLockTemplate : CheckTimeTableFairLock {
    override fun isHeldByCurrentThread(name: String): Boolean {
        val lock = FairLockStore.getFairLock(name)
        return lock.isHeldByCurrentThread
    }
}
