package com.reservation.batch.timetable.step.writer

import com.reservation.persistence.timetable.entity.TimeTableEntity
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import javax.sql.DataSource

class TimeTableJdbcItemWriter(
    private val dataSource: DataSource,
) : ItemWriter<List<TimeTableEntity>> {
    companion object {
        private const val BATCH_SIZE = 100
        private const val INSERT_SQL = """
            INSERT INTO timetable (
                id, restaurant_id, date, day,
                start_time, end_time, table_number, table_size,
                tableStatus
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
    }

    override fun write(chunk: Chunk<out List<TimeTableEntity>>) {
        val flat: List<TimeTableEntity> = chunk.flatten()

        if (flat.isEmpty()) return

        dataSource.connection.use { connection ->

            connection.prepareStatement(INSERT_SQL).use { ps ->
                flat.forEachIndexed { index, item ->
                    ps.setString(1, item.id)
                    ps.setString(2, item.restaurantId)
                    ps.setDate(3, java.sql.Date.valueOf(item.date))
                    ps.setString(4, item.day.name)
                    ps.setTime(5, java.sql.Time.valueOf(item.startTime))
                    ps.setTime(6, java.sql.Time.valueOf(item.endTime))
                    ps.setInt(7, item.tableNumber)
                    ps.setInt(8, item.tableSize)
                    ps.setString(9, item.tableStatus.name)

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
