package com.reservation.schedule.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.schedule.Schedule
import com.reservation.schedule.policy.form.CreateHolidayForm
import com.reservation.schedule.port.input.CreateHolidayUseCase
import com.reservation.schedule.port.input.command.request.CreateHolidayCommand
import com.reservation.schedule.port.mutator.ScheduleInquiryMutator
import com.reservation.schedule.port.mutator.ScheduleMutator
import com.reservation.schedule.port.output.ChangeSchedule
import com.reservation.schedule.port.output.LoadSchedule
import com.reservation.schedule.service.CreateHolidayDomainService
import com.reservation.schedule.snapshot.ScheduleSnapshot
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateHolidayService(
    private val createHolidayDomainService: CreateHolidayDomainService,
    private val loadSchedule: LoadSchedule,
    private val changeSchedule: ChangeSchedule,
) : CreateHolidayUseCase {
    private fun createHolidayForm(command: CreateHolidayCommand): CreateHolidayForm =
        CreateHolidayForm(command.restaurantId, command.date)

    private fun load(restaurantId: String): Schedule {
        val result = loadSchedule.query(restaurantId) ?: throw NoSuchPersistedElementException()
        return ScheduleMutator.mutate(result)
    }

    private fun change(snapshot: ScheduleSnapshot): Boolean {
        val inquiry = ScheduleInquiryMutator.mutate(snapshot)
        return changeSchedule.command(inquiry)
    }

    @Transactional
    override fun execute(command: CreateHolidayCommand): Boolean {
        val schedule = load(command.restaurantId)
        val form = createHolidayForm(command)
        val snapshot = createHolidayDomainService.create(schedule, form)

        return change(snapshot)
    }
}
