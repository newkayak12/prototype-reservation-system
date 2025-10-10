package com.reservation.batch.timetable.step.writer

import com.reservation.batch.annotations.Step
import com.reservation.persistence.timetable.entity.TimeTableEntity
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import javax.sql.DataSource

@Step
class TimeTableJdbcItemWriter(
    private val dataSource: DataSource,
) : ItemWriter<List<TimeTableEntity>> {
    companion object {
        private const val BATCH_SIZE = 100
        private const val INSERT_SQL = """
            INSERT INTO timetable (
                id, restaurant_id, date, day,
                start_time, end_time, table_number, table_size,
                table_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        private const val ID_PLACE_NUMBER = 1
        private const val RESTAURANT_ID_PLACE_NUMBER = 2
        private const val DATE_PLACE_NUMBER = 3
        private const val DAY_PLACE_NUMBER = 4
        private const val START_TIME_PLACE_NUMBER = 5
        private const val END_TIME_PLACE_NUMBER = 6
        private const val TABLE_NUMBER_PLACE_NUMBER = 7
        private const val TABLE_SIZE_PLACE_NUMBER = 8
        private const val TABLE_STATUS_PLACE_NUMBER = 9
    }

    override fun write(chunk: Chunk<out List<TimeTableEntity>>) {
        val flat: List<TimeTableEntity> = chunk.flatten()

        if (flat.isEmpty()) return

        dataSource.connection.use { connection ->

            connection.prepareStatement(INSERT_SQL).use { ps ->
                flat.forEachIndexed { index, item ->
                    ps.setString(ID_PLACE_NUMBER, item.id)
                    ps.setString(RESTAURANT_ID_PLACE_NUMBER, item.restaurantId)
                    ps.setDate(DATE_PLACE_NUMBER, java.sql.Date.valueOf(item.date))
                    ps.setString(DAY_PLACE_NUMBER, item.day.name)
                    ps.setTime(START_TIME_PLACE_NUMBER, java.sql.Time.valueOf(item.startTime))
                    ps.setTime(END_TIME_PLACE_NUMBER, java.sql.Time.valueOf(item.endTime))
                    ps.setInt(TABLE_NUMBER_PLACE_NUMBER, item.tableNumber)
                    ps.setInt(TABLE_SIZE_PLACE_NUMBER, item.tableSize)
                    ps.setString(TABLE_STATUS_PLACE_NUMBER, item.tableStatus.name)

                    ps.addBatch()

                    if ((index + 1) % BATCH_SIZE == 0) {
                        ps.executeBatch()
                        ps.clearBatch()
                    }
                }

                ps.executeBatch() // 남은 것 처리
            }
        }
    }
}
