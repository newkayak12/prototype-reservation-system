package com.reservation.batch.timetable.constants

object TimeTableBatchConstants {
    object JobParameter {
        const val DATE_KEY = "date"
    }

    object Job {
        const val NAME = "timeTableJob"
    }

    object Step {
        const val NAME = "time_table_step"
    }

    object Reader {
        const val SCHEDULE_NAME = "schedule-reader"
        const val COMPOSITE_NAME = "composite_reader"
    }

    object Processor {
        const val NAME = "time_table_processor"
    }

    object Writer {
        const val NAME = "time_table_writer"
    }
}
