package com.reservation.batch.timetable.step.processor

import com.reservation.batch.timetable.dto.ScheduleWithData
import com.reservation.batch.timetable.step.processor.TimeTableProcessorTest.TestProcessorConfig
import com.reservation.enumeration.ScheduleActiveStatus.ACTIVE
import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.schedule.entity.TableEntity
import com.reservation.persistence.schedule.entity.TimeSpanEntity
import com.reservation.utilities.generator.uuid.UuidGenerator
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepExecution
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Testcontainers
@ContextConfiguration(classes = [TestProcessorConfig::class])
@ExtendWith(SpringExtension::class)
class TimeTableProcessorTest {
    class TestProcessorConfig {
        @Bean
        fun timeTableItemProcessor(): TimeTableItemProcessor = TimeTableItemProcessor()
    }

    @Autowired
    private lateinit var timeTableItemProcessor: TimeTableItemProcessor

    @DisplayName("Processor를 호출하며")
    @Nested
    inner class `Call Processor` {
        private fun giveMeActiveScheduleFixture(restaurantId: String) =
            ScheduleEntity(
                restaurantId,
                tablesConfigured = true,
                workingHoursConfigured = true,
                holidaysConfigured = true,
                ACTIVE,
                2,
                8,
            )

        private fun giveMeTimeSpan(
            restaurantId: String,
            dayOfWeek: DayOfWeek,
        ) = listOf(
            TimeSpanEntity(restaurantId, dayOfWeek, LocalTime.of(13, 0), LocalTime.of(14, 0)),
            TimeSpanEntity(restaurantId, dayOfWeek, LocalTime.of(14, 0), LocalTime.of(15, 0)),
        )

        private fun giveMeTable(restaurantId: String) =
            listOf(
                TableEntity(restaurantId, 1, 4),
                TableEntity(restaurantId, 4, 4),
            )

        private fun createStepExecutionManually(): StepExecution {
            val jobParams =
                JobParametersBuilder()
                    .addString("DATE_KEY", LocalDate.now().toString())
                    .toJobParameters()

            val jobExecution = JobExecution(1L, jobParams)
            return StepExecution("step", jobExecution)
        }

        @Test
        fun `should process ScheduleWithData correctly`() {
            val uuid = UuidGenerator.generate()
            val now = LocalDate.now()
            val schedule = giveMeActiveScheduleFixture(uuid)
            val timeSpans = giveMeTimeSpan(uuid, now.dayOfWeek)
            val tables = giveMeTable(uuid)
            val scheduleWithData =
                ScheduleWithData(
                    schedule = schedule,
                    holidays = emptyList(),
                    timeSpans = timeSpans,
                    tables = tables,
                )

//            every {
//                timeTableCompositeItemReader.read()
//            } returns scheduleWithData

            val stepExecution = createStepExecutionManually()
            timeTableItemProcessor.beforeStep(stepExecution)

            val result = timeTableItemProcessor.process(scheduleWithData)

            println("RESULT $result")
        }
    }
}
