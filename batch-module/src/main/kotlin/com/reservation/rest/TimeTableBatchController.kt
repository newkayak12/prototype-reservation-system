package com.reservation.rest

import com.reservation.batch.timetable.constants.TimeTableBatchConstants
import com.reservation.batch.timetable.constants.TimeTableBatchConstants.JobParameter
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
class TimeTableBatchController(
    private val jobLauncher: JobLauncher,
    @Qualifier(TimeTableBatchConstants.Job.NAME)
    private val timeTableJob: Job,
) {
    @PostMapping(TimeTableBatchUrl.FIRE)
    fun fireTimeTableBatch() {
        val jobParameter =
            JobParametersBuilder()
                .addLocalDate(JobParameter.DATE_KEY, YearMonth.now().atDay(1))
                .toJobParameters()
        jobLauncher.run(timeTableJob, jobParameter)
    }
}
