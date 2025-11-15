package com.reservation.schedule.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.schedule.Schedule
import com.reservation.schedule.policy.form.CreateTimeSpanForm
import com.reservation.schedule.port.input.CreateTimeSpanUseCase
import com.reservation.schedule.port.input.command.request.CreateTimeSpanCommand
import com.reservation.schedule.port.mutator.ScheduleInquiryMutator
import com.reservation.schedule.port.mutator.ScheduleMutator
import com.reservation.schedule.port.output.ChangeSchedule
import com.reservation.schedule.port.output.LoadSchedule
import com.reservation.schedule.service.CreateTimeSpanDomainService
import com.reservation.schedule.snapshot.ScheduleSnapshot
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateTimeSpanService(
    private val createTimeSpanDomainService: CreateTimeSpanDomainService,
    private val loadSchedule: LoadSchedule,
    private val changeSchedule: ChangeSchedule,
) : CreateTimeSpanUseCase {
    private fun createTimeSpanForm(command: CreateTimeSpanCommand): CreateTimeSpanForm =
        CreateTimeSpanForm(
            restaurantId = command.restaurantId,
            day = command.day,
            startTime = command.startTime,
            endTime = command.endTime,
        )

    private fun load(restaurantId: String): Schedule {
        val result = loadSchedule.query(restaurantId) ?: throw NoSuchPersistedElementException()
        return ScheduleMutator.mutate(result)
    }

    private fun change(snapshot: ScheduleSnapshot): Boolean {
        val inquiry = ScheduleInquiryMutator.mutate(snapshot)
        return changeSchedule.command(inquiry)
    }

    @Transactional
    override fun execute(command: CreateTimeSpanCommand): Boolean {
        val schedule = load(command.restaurantId)
        val form = createTimeSpanForm(command)
        val snapshot = createTimeSpanDomainService.create(schedule, form)

        return change(snapshot)
    }
}
