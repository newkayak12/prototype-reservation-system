package com.reservation.schedule.usecase

import com.reservation.schedule.port.input.CreateScheduleUseCase
import com.reservation.schedule.port.input.command.request.CreateScheduleCommand
import com.reservation.schedule.port.output.CreateSchedule
import com.reservation.schedule.port.output.CreateSchedule.CreateHolidayInquiry
import com.reservation.schedule.port.output.CreateSchedule.CreateScheduleInquiry
import com.reservation.schedule.port.output.CreateSchedule.CreateTimeSpanInquiry
import com.reservation.schedule.service.CreateScheduleDomainService
import com.reservation.schedule.snapshot.HolidaySnapshot
import com.reservation.schedule.snapshot.TimeSpanSnapshot
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreateScheduleService(
    private val createSchedule: CreateSchedule,
    private val createScheduleDomainService: CreateScheduleDomainService,
) : CreateScheduleUseCase {

    private fun mapTimespan(it: TimeSpanSnapshot) =
        CreateTimeSpanInquiry(
            id = it.id,
            restaurantId = it.restaurantId,
            day = it.day,
            startTime = it.startTime,
            endTime = it.endTime,
        )

    private fun mapHoliday(it: HolidaySnapshot) =
        CreateHolidayInquiry(
            id = it.id,
            restaurantId = it.restaurantId,
            date = it.date,
        )

    @Transactional
    override fun execute(command: CreateScheduleCommand): Boolean {

        val snapshot = createScheduleDomainService.create(command.restaurantId)

        val inquiry = CreateScheduleInquiry(
            restaurantId = snapshot.restaurantId,
            timeSpans = snapshot.timeSpans.map(this::mapTimespan),
            holidays = snapshot.holidays.map(this::mapHoliday)
        )

        return createSchedule.command(inquiry)
    }
}
