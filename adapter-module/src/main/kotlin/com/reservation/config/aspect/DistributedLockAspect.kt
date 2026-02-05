package com.reservation.config.aspect

import com.reservation.config.annotations.DistributedLock
import com.reservation.enumeration.LockType
import com.reservation.enumeration.LockType.FAIR_LOCK
import com.reservation.enumeration.LockType.LOCK
import com.reservation.redis.redisson.lock.LockCoordinator
import com.reservation.redis.redisson.lock.fair.FairLockRedisCoordinator
import com.reservation.redis.redisson.lock.general.GeneralLockRedisCoordinator
import com.reservation.redis.redisson.lock.named.NamedLockCoordinator
import com.reservation.timetable.exceptions.RequestUnProcessableException
import com.reservation.timetable.exceptions.TooManyRequestHasBeenComeSimultaneouslyException
import com.reservation.utilities.logger.loggerFactory
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.redisson.client.RedisException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class DistributedLockAspect(
    private val fairLockRedisCoordinator: FairLockRedisCoordinator,
    private val generalLockRedisCoordinator: GeneralLockRedisCoordinator,
    private val namedLockCoordinator: NamedLockCoordinator,
    private val spelParser: SpelParser,
    private val transactionManager: PlatformTransactionManager,
) {
    private val log = loggerFactory<DistributedLockAspect>()

    private val serializableTemplate by lazy {
        TransactionTemplate(transactionManager)
            .apply {
                isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
            }
    }

    private fun parseKey(
        proceedingJoinPoint: ProceedingJoinPoint,
        distributedLock: DistributedLock,
    ): String {
        val expression = distributedLock.key
        val method = (proceedingJoinPoint.signature as MethodSignature).method
        val args = proceedingJoinPoint.args
        return spelParser.parse(expression, method, args)
    }

    private fun getRedisLockCoordinator(lockType: LockType) =
        when (lockType) {
            LOCK -> generalLockRedisCoordinator
            FAIR_LOCK -> fairLockRedisCoordinator
        }

    private fun acquireRedisLock(
        parsedKey: String,
        distributedLock: DistributedLock,
    ) {
        val lockType = distributedLock.lockType
        val waitTime = distributedLock.waitTime
        val waitTimeUnit = distributedLock.waitTimeUnit
        val isAcquired: Boolean =
            getRedisLockCoordinator(lockType = lockType)
                .tryLock(parsedKey, waitTime, waitTimeUnit)
        if (!isAcquired) throw TooManyRequestHasBeenComeSimultaneouslyException()
    }

    private fun releaseLockRedisTemplate(
        parsedKey: String,
        distributedLock: DistributedLock,
    ) {
        val lockType = distributedLock.lockType
        val lockCoordinator: LockCoordinator = getRedisLockCoordinator(lockType)

        if (!lockCoordinator.isHeldByCurrentThread(parsedKey)) return
        lockCoordinator.unlock(parsedKey)
    }

    // NamedLock으로 변경
    fun executeSerialization(proceedingJoinPoint: ProceedingJoinPoint): Any? {
        return runCatching<Any?> { serializableTemplate.execute { proceedingJoinPoint.proceed() } }
            .getOrElse { exception ->
                when (exception) {
                    is DataIntegrityViolationException,
                    is CannotGetJdbcConnectionException,
                    is TransientDataAccessException,
                    -> throw RequestUnProcessableException()

                    else -> throw exception
                }
            }
    }

    @Suppress("ThrowsCount")
    private fun acquireDatabaseLock(
        parsedKey: String,
        distributedLock: DistributedLock,
    ) {
        val waitTime = distributedLock.waitTime
        val waitTimeUnit = distributedLock.waitTimeUnit

        val isAcquired =
            runCatching<Boolean> {
                namedLockCoordinator.tryLock(
                    parsedKey,
                    waitTime,
                    waitTimeUnit,
                )
            }.getOrElse { exception ->
                when (exception) {
                    is DataIntegrityViolationException,
                    is CannotGetJdbcConnectionException,
                    is TransientDataAccessException,
                    -> throw RequestUnProcessableException()

                    else -> throw exception
                }
            }

        if (!isAcquired) throw TooManyRequestHasBeenComeSimultaneouslyException()
    }

    private fun releaseDatabaseLock(parsedKey: String) {
        namedLockCoordinator.unlock(parsedKey)
    }

    fun executeNamedLock(
        parsedKey: String,
        proceedingJoinPoint: ProceedingJoinPoint,
        distributedLock: DistributedLock,
    ): Any? {
        var doRelease = true
        try {
            acquireDatabaseLock(parsedKey, distributedLock)
            return proceedingJoinPoint.proceed()
        } catch (e: TooManyRequestHasBeenComeSimultaneouslyException) {
            doRelease = false
            throw e
        } finally {
            if (doRelease) {
                releaseDatabaseLock(parsedKey)
            }
        }
    }

    @Around("@annotation(com.reservation.config.annotations.DistributedLock)")
    @Suppress(
        "UseCheckOrError",
        "RethrowCaughtException",
        "SwallowedException",
    )
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
        var isRedisAccessible = true
        try {
            acquireRedisLock(parsedKey, distributedLock)
            return proceedingJoinPoint.proceed()
        } catch (e: TooManyRequestHasBeenComeSimultaneouslyException) {
            doRelease = false
            throw e
        } catch (e: RedisException) {
            log.warn(" Unable to connect to Redis")
            isRedisAccessible = false
            return executeNamedLock(parsedKey, proceedingJoinPoint, distributedLock)
        } finally {
            if (isRedisAccessible && doRelease) releaseLockRedisTemplate(parsedKey, distributedLock)
        }
    }
}
