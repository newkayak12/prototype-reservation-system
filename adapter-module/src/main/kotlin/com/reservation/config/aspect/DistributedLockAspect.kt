package com.reservation.config.aspect

import com.reservation.config.annotations.DistributedLock
import com.reservation.enumeration.LockType
import com.reservation.enumeration.LockType.FAIR_LOCK
import com.reservation.enumeration.LockType.LOCK
import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.CheckLockTemplate
import com.reservation.redis.redisson.lock.UnlockLockTemplate
import com.reservation.timetable.exception.TooManyRequestHasBeenComeSimultaneouslyException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Suppress("LongParameterList")
class DistributedLockAspect(
    private val acquireFairLockAdapter: AcquireLockTemplate,
    private val checkFairLockAdapter: CheckLockTemplate,
    private val unlockFairLockAdapter: UnlockLockTemplate,
    private val acquireLockAdapter: AcquireLockTemplate,
    private val checkLockAdapter: CheckLockTemplate,
    private val unlockLockAdapter: UnlockLockTemplate,
    private val spelParser: SpelParser,
) {
    private fun parseKey(
        proceedingJoinPoint: ProceedingJoinPoint,
        distributedLock: DistributedLock,
    ): String {
        val expression = distributedLock.key
        val method = (proceedingJoinPoint.signature as MethodSignature).method
        val args = proceedingJoinPoint.args
        return spelParser.parse(expression, method, args)
    }

    private fun getAcquireLock(lockType: LockType) =
        when (lockType) {
            LOCK -> acquireLockAdapter
            FAIR_LOCK -> acquireFairLockAdapter
        }

    private fun acquireLock(
        parsedKey: String,
        distributedLock: DistributedLock,
    ) {
        val lockType = distributedLock.lockType
        val waitTime = distributedLock.waitTime
        val waitTimeUnit = distributedLock.waitTimeUnit
        val isAcquired: Boolean =
            getAcquireLock(lockType = lockType)
                .tryLock(parsedKey, waitTime, waitTimeUnit)
        if (!isAcquired) throw TooManyRequestHasBeenComeSimultaneouslyException()
    }

    private fun getCheckLock(lockType: LockType) =
        when (lockType) {
            LOCK -> checkLockAdapter
            FAIR_LOCK -> checkFairLockAdapter
        }

    private fun getUnLock(lockType: LockType) =
        when (lockType) {
            LOCK -> unlockLockAdapter
            FAIR_LOCK -> unlockFairLockAdapter
        }

    private fun releaseLock(
        parsedKey: String,
        distributedLock: DistributedLock,
    ) {
        val lockType = distributedLock.lockType
        val checkLockTemplate: CheckLockTemplate =
            getCheckLock(lockType)
        val unlockLockTemplate: UnlockLockTemplate =
            getUnLock(lockType)

        if (!checkLockTemplate.isHeldByCurrentThread(parsedKey)) return
        unlockLockTemplate.unlock(parsedKey)
    }

    @Around("@annotation(com.reservation.config.annotations.DistributedLock)")
    @Suppress("UseCheckOrError", "RethrowCaughtException")
    fun executeDistributedLockAction(proceedingJoinPoint: ProceedingJoinPoint): Any? {
        val method = (proceedingJoinPoint.signature as MethodSignature).method
        val targetClass = proceedingJoinPoint.target::class.java
        val targetMethod = targetClass.getMethod(method.name, *method.parameterTypes)
        val distributedLock =
            targetMethod.getAnnotation(DistributedLock::class.java)
                ?: method.getAnnotation(DistributedLock::class.java)
                ?: throw IllegalStateException(
                    "@DistributedLock annotation not found on method ${method.name}",
                )
        val parsedKey = parseKey(proceedingJoinPoint, distributedLock)
        var doRelease = true
        try {
            acquireLock(parsedKey, distributedLock)
            return proceedingJoinPoint.proceed()
        } catch (e: TooManyRequestHasBeenComeSimultaneouslyException) {
            doRelease = false
            throw e
        } finally {
            if (doRelease) releaseLock(parsedKey, distributedLock)
        }
    }
}
