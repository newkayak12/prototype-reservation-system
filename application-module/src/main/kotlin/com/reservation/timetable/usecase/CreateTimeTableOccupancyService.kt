package com.reservation.timetable.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.exceptions.ClientException
import com.reservation.timetable.TimeTable
import com.reservation.timetable.exception.AllTheSeatsAreAlreadyOccupiedException
import com.reservation.timetable.exception.AllTheThingsAreAlreadyOccupiedException
import com.reservation.timetable.exception.TooManyCreateTimeTableOccupancyRequestException
import com.reservation.timetable.exception.TooManyRequestHasBeenComeSimultaneouslyException
import com.reservation.timetable.port.input.CreateTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import com.reservation.timetable.port.output.AcquireTimeTableFairLock
import com.reservation.timetable.port.output.AcquireTimeTableRateLimiter
import com.reservation.timetable.port.output.AcquireTimeTableRateLimiter.RateLimitSettings
import com.reservation.timetable.port.output.AcquireTimeTableRateLimiter.RateLimitType.WHOLE
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore.SemaphoreInquiry
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore.SemaphoreSettings
import com.reservation.timetable.port.output.CheckTimeTableFairLock
import com.reservation.timetable.port.output.CreateTimeTableOccupancy
import com.reservation.timetable.port.output.CreateTimeTableOccupancy.CreateTimeTableOccupancyInquiry
import com.reservation.timetable.port.output.CreateTimeTableOccupancy.TimetableOccupancyInquiry
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.port.output.LoadBookableTimeTables.LoadBookableTimeTablesInquiry
import com.reservation.timetable.port.output.ReleaseSemaphore
import com.reservation.timetable.port.output.UnlockTimeTableFairLock
import com.reservation.timetable.service.CreateTimeTableOccupancyDomainService
import com.reservation.timetable.snapshot.TimeTableSnapshot
import com.reservation.timetable.snapshot.TimetableOccupancySnapShot
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@UseCase
@Suppress("LongParameterList", "TooManyFunctions")
class CreateTimeTableOccupancyService(
    private val acquireTimeTableRateLimiter: AcquireTimeTableRateLimiter,
    private val acquireTimeTableFairLock: AcquireTimeTableFairLock,
    private val checkTimeTableFairLock: CheckTimeTableFairLock,
    private val unlockTimeTableFairLock: UnlockTimeTableFairLock,
    private val acquireTimeTableSemaphore: AcquireTimeTableSemaphore,
    private val releaseSemaphore: ReleaseSemaphore,
    private val loadBookableTimeTables: LoadBookableTimeTables,
    private val createTimeTableOccupancy: CreateTimeTableOccupancy,
    private val createTimeTableOccupancyDomainService: CreateTimeTableOccupancyDomainService,
) : CreateTimeTableOccupancyUseCase {
    companion object {
        private const val FAIR_LOCK_MAXIMUM_WAIT_TIME = 2L
        private const val RATE_LIMITER_CAPACITY = 1000L
        private const val RATE_LIMITER_RATE_INTERVAL = 1L
        private const val RATE_LIMITER_DURATION = 1L
        private const val RATE_LIMIT_MAXIMUM_WAIT_TIME = 3L
        private const val SEMAPHORE_DURATION = 10L
        private const val SEMAPHORE_MAXIMUM_WAIT_TIME = 5L
        private const val SEMAPHORE_ACQUIRE_SIZE = 1
        private const val NAME = "TIMETABLE"
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")
    }

    private fun key(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
    ) = "$NAME:$restaurantId:${date.format(DATE_FORMATTER)}:${startTime.format(TIME_FORMATTER)}"

    private fun acquireRateLimiter(key: String) {
        val rateLimitSettings =
            RateLimitSettings(
                type = WHOLE,
                rate = RATE_LIMITER_CAPACITY,
                rateInterval = Duration.ofMinutes(RATE_LIMITER_RATE_INTERVAL),
                duration = Duration.ofHours(RATE_LIMITER_DURATION),
            )
        val isAcquired =
            acquireTimeTableRateLimiter.tryAcquire(
                key,
                Duration.ofMinutes(RATE_LIMIT_MAXIMUM_WAIT_TIME),
                rateLimitSettings,
            )

        if (!isAcquired) throw TooManyCreateTimeTableOccupancyRequestException()
    }

    private fun acquireFairLock(key: String) {
        val isAcquired =
            acquireTimeTableFairLock.tryLock(
                key,
                FAIR_LOCK_MAXIMUM_WAIT_TIME,
                TimeUnit.MINUTES,
            )
        if (!isAcquired) throw TooManyRequestHasBeenComeSimultaneouslyException()
    }

    private fun releaseFairLock(key: String) {
        if (!checkTimeTableFairLock.isHeldByCurrentThread(key)) return
        unlockTimeTableFairLock.unlock(key)
    }

    private fun loadBookableTimeTables(command: CreateTimeTableOccupancyCommand): List<TimeTable> {
        val inquiry =
            LoadBookableTimeTablesInquiry(
                restaurantId = command.restaurantId,
                date = command.date,
                startTime = command.startTime,
            )
        val list = loadBookableTimeTables.query(inquiry)
        if (list.isEmpty()) throw AllTheSeatsAreAlreadyOccupiedException()

        return list
    }

    private fun acquireSemaphore(
        key: String,
        size: Int,
    ) {
        val semaphoreSettings =
            SemaphoreSettings(
                SEMAPHORE_ACQUIRE_SIZE,
                Duration.ofMinutes(SEMAPHORE_MAXIMUM_WAIT_TIME),
            )
        val semaphoreInquiry = SemaphoreInquiry(size, Duration.ofMinutes(SEMAPHORE_DURATION))
        val isAcquired =
            acquireTimeTableSemaphore.tryAcquire(
                key,
                semaphoreSettings,
                semaphoreInquiry,
            )

        if (!isAcquired) throw AllTheThingsAreAlreadyOccupiedException()
    }

    private fun releaseSemaphore(key: String) {
        releaseSemaphore.release(key)
    }

    private fun saveOccupancy(
        userId: String,
        timeTables: List<TimeTable>,
    ): Boolean {
        for (timetable in timeTables.sortedBy { it.id }) {
            val snapshot = createTimeTableOccupancyDomainService.create(userId, timetable)
            val result = createTimeTableOccupancy.createTimeTableOccupancy(snapshot.toInquiry())

            if (result) break
        }

        return true
    }

    @Transactional
    override fun execute(command: CreateTimeTableOccupancyCommand): Boolean {
        val key = key(command.restaurantId, command.date, command.startTime)

        acquireRateLimiter(key)
        acquireFairLock(key)

        try {
            val list = loadBookableTimeTables(command)
            acquireSemaphore(key, list.size)
            return saveOccupancy(command.userId, list)
        } catch (e: ClientException) {
            when (e) {
                is AllTheSeatsAreAlreadyOccupiedException -> throw e
                else -> {
                    releaseSemaphore(key)
                    throw e
                }
            }
        } finally {
            releaseFairLock(key)
        }
    }

    private fun TimeTableSnapshot.toInquiry(): CreateTimeTableOccupancyInquiry =
        CreateTimeTableOccupancyInquiry(
            id = id!!,
            restaurantId = restaurantId,
            date = date,
            day = day,
            startTime = startTime,
            endTime = endTime,
            tableNumber = tableNumber,
            tableSize = tableSize,
            tableStatus = tableStatus,
            timetableOccupancy = timetableOccupancy!!.toInquiry(),
        )

    private fun TimetableOccupancySnapShot.toInquiry(): TimetableOccupancyInquiry =
        TimetableOccupancyInquiry(
            id = id,
            timeTableId = timeTableId,
            userId = userId,
            occupiedStatus = occupiedStatus,
            occupiedDatetime = occupiedDatetime,
            unoccupiedDatetime = unoccupiedDatetime,
        )
}
