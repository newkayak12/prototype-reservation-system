package com.reservation.batch.timetable.job

import com.reservation.batch.timetable.constants.TimeTableBatchConstants
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
@DependsOn("time_table_step_config")
class TimeTableJobConfig(
    private val jobRepository: JobRepository,
) {
    @Bean(TimeTableBatchConstants.Job.NAME)
    fun timeTableJob(
        @Qualifier(value = TimeTableBatchConstants.Step.NAME) timeTableStep: Step,
    ): Job {
        return JobBuilder(TimeTableBatchConstants.Job.NAME, jobRepository)
            .start(timeTableStep)
            .build()
    }
}
