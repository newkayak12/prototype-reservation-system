package com.reservation.event.schedule

import com.reservation.event.schedule.adapter.ScheduleEventListener
import com.reservation.restaurant.event.CreateScheduleEvent
import com.reservation.schedule.port.input.CreateScheduleUseCase
import com.reservation.schedule.port.input.command.request.CreateScheduleCommand
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@DisplayName("Schedule 저장 이벤트를 발행한다.")
class ScheduleEventListenerTest : BehaviorSpec(
    {
        lateinit var mockCreateScheduleUseCase: CreateScheduleUseCase
        lateinit var applicationContextRunner: ApplicationContextRunner

        beforeSpec {
            mockCreateScheduleUseCase = mockk<CreateScheduleUseCase>()
            applicationContextRunner = ApplicationContextRunner()
        }

        Given("CreateScheduleEvent가 발행되었을 때") {
            val restaurantId = "restaurant-123"
            val event = CreateScheduleEvent(restaurantId)

            When("이벤트를 발행하면") {

                every {
                    mockCreateScheduleUseCase.execute(any())
                } returns true

                applicationContextRunner
                    .withBean(CreateScheduleUseCase::class.java, { mockCreateScheduleUseCase })
                    .withBean(ScheduleEventListener::class.java)
                    .run { context ->
                        context.publishEvent(event)
                    }

                Then("리스너가 호출되어 UseCase가 실행되어야 함") {
                    verify(exactly = 1) {
                        mockCreateScheduleUseCase.execute(
                            CreateScheduleCommand(restaurantId),
                        )
                    }
                }
            }
        }
    },
)
