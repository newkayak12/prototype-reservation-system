package com.reservation.timetable.usecase

import com.reservation.config.annotations.DistributedLock
import com.reservation.config.annotations.RateLimiter
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.LockType
import com.reservation.enumeration.RateLimitType
import com.reservation.exceptions.ClientException
import com.reservation.timetable.TimeTable
import com.reservation.timetable.event.TimeTableOccupiedDomainEvent
import com.reservation.timetable.exceptions.AllTheSeatsAreAlreadyOccupiedException
import com.reservation.timetable.exceptions.AllTheThingsAreAlreadyOccupiedException
import com.reservation.timetable.port.input.CreateTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore.SemaphoreInquiry
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore.SemaphoreSettings
import com.reservation.timetable.port.output.CreateTimeTableOccupancy
import com.reservation.timetable.port.output.CreateTimeTableOccupancy.CreateTimeTableOccupancyInquiry
import com.reservation.timetable.port.output.CreateTimeTableOccupancy.TimetableOccupancyInquiry
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.port.output.LoadBookableTimeTables.LoadBookableTimeTablesInquiry
import com.reservation.timetable.port.output.ReleaseSemaphore
import com.reservation.timetable.service.CreateTimeTableOccupancyDomainService
import com.reservation.timetable.service.CreateTimeTableOccupiedDomainEventService
import com.reservation.timetable.snapshot.TimeTableSnapshot
import com.reservation.timetable.snapshot.TimetableOccupancySnapShot
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@UseCase
@Suppress("LongParameterList", "TooManyFunctions")
class CreateTimeTableOccupancyService(
    private val acquireTimeTableSemaphore: AcquireTimeTableSemaphore,
    private val releaseSemaphore: ReleaseSemaphore,
    private val loadBookableTimeTables: LoadBookableTimeTables,
    private val createTimeTableOccupancy: CreateTimeTableOccupancy,
    private val createTimeTableOccupancyDomainService: CreateTimeTableOccupancyDomainService,
    private val createTimeTableOccupiedDomainEventService:
        CreateTimeTableOccupiedDomainEventService,
    private val applicationEventPublisher: ApplicationEventPublisher,
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
        private const val SEMAPHORE_NAME = "SEMAPHORE"
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")
        private const val RATE_LIMITER_SP_EL_KEY = """
         'RATE_LIMITER:' + #command.restaurantId + ':' +
          #command.date.format(T(java.time.format.DateTimeFormatter).ofPattern('yyyyMMdd')) + ':' +
          #command.startTime.format(T(java.time.format.DateTimeFormatter).ofPattern('HHmm'))
        """
        private const val DISTRIBUTED_LOCK_SP_EL_KEY = """
         'DISTRIBUTED_LOCK:' + #command.restaurantId + ':' +
          #command.date.format(T(java.time.format.DateTimeFormatter).ofPattern('yyyyMMdd')) + ':' +
          #command.startTime.format(T(java.time.format.DateTimeFormatter).ofPattern('HHmm'))
        """
    }

    private fun key(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
    ) = "$SEMAPHORE_NAME:$restaurantId:${date.format(DATE_FORMATTER)}:${
        startTime.format(TIME_FORMATTER)
    }"

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
    ): TimeTableOccupiedDomainEvent {
        for (timetable in timeTables.sortedBy { it.id }) {
            val snapshot = createTimeTableOccupancyDomainService.create(userId, timetable)
            val inquiry = snapshot.toInquiry(userId)
            val timetableId = snapshot.id
            val occupancyId = createTimeTableOccupancy.createTimeTableOccupancy(inquiry)

            if (timetableId == null || occupancyId == null) break

            return createTimeTableOccupiedDomainEventService.create(timetableId, occupancyId!!)
        }

        throw AllTheThingsAreAlreadyOccupiedException()
    }

    private fun saveToOutBoxAndPublish(domainEvent: TimeTableOccupiedDomainEvent): Boolean {
        applicationEventPublisher.publishEvent(domainEvent)
        return true
    }

    @RateLimiter(
        key = RATE_LIMITER_SP_EL_KEY,
        type = RateLimitType.WHOLE,
        rate = RATE_LIMITER_CAPACITY,
        maximumWaitTime = RATE_LIMIT_MAXIMUM_WAIT_TIME,
        rateIntervalTime = RATE_LIMITER_RATE_INTERVAL,
        bucketLiveTime = RATE_LIMITER_DURATION,
    )
    @DistributedLock(
        key = DISTRIBUTED_LOCK_SP_EL_KEY,
        lockType = LockType.FAIR_LOCK,
        waitTime = FAIR_LOCK_MAXIMUM_WAIT_TIME,
        waitTimeUnit = TimeUnit.MINUTES,
    )
    @Transactional
    override fun execute(command: CreateTimeTableOccupancyCommand): Boolean {
        val key = key(command.restaurantId, command.date, command.startTime)

        try {
            val list = loadBookableTimeTables(command)
            acquireSemaphore(key, list.size)
            val domainEvent = saveOccupancy(command.userId, list)

            return saveToOutBoxAndPublish(domainEvent)
        } catch (e: ClientException) {
            when (e) {
                is AllTheThingsAreAlreadyOccupiedException -> throw e
                is AllTheSeatsAreAlreadyOccupiedException -> throw e
                else -> {
                    releaseSemaphore(key)
                    throw e
                }
            }
        } catch (e: DataIntegrityViolationException) {
            releaseSemaphore(key)
            throw e
        }
    }

    private fun TimeTableSnapshot.toInquiry(userId: String): CreateTimeTableOccupancyInquiry =
        CreateTimeTableOccupancyInquiry(
            id = id!!,
            restaurantId = restaurantId,
            userId = userId,
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
