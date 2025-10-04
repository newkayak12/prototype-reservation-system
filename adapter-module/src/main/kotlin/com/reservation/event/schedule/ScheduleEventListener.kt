package com.reservation.event.schedule

import com.reservation.restaurant.event.CreateScheduleEvent
import com.reservation.schedule.port.input.CreateScheduleUseCase
import com.reservation.schedule.port.input.command.request.CreateScheduleCommand
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ScheduleEventListener(
    private val createScheduleUseCase: CreateScheduleUseCase,
) {

    @EventListener
    fun handleCreateScheduleEvent(event: CreateScheduleEvent) {

        val restaurantId = event.restaurantId
        val command = CreateScheduleCommand(restaurantId)


        createScheduleUseCase.execute(command)
    }
}
