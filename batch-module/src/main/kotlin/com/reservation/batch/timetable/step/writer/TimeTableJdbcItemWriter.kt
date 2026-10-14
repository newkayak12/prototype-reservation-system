package com.reservation.batch.timetable.step.writer

import com.reservation.batch.annotations.Step
import com.reservation.persistence.timetable.entity.TimeTableEntity
import com.reservation.utilities.generator.uuid.UuidBinaryCodec
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import javax.sql.DataSource

/**
 * binary-pk 회귀 수정 (2026-10-06): `id`/`restaurant_id`는 `timetable` 테이블에서
 * `BINARY(16)`이다(`TimeTableEntity`가 `TimeBasedPrimaryKey`를 상속하고
 * `restaurantId`에 `UuidBinaryConverter`를 선언한 것과 짝을 맞춘 운영 스키마). 이 writer는
 * **JPA를 거치지 않는 raw JDBC 배치 insert**라서 Hibernate의 변환자(`UuidBinaryIdentifierType`
 * / `UuidBinaryConverter`)가 전혀 적용되지 않는다 — `ps.setString`으로 36자 평문 UUID를 그대로
 * 보내면 `Data truncation: Data too long for column 'id'`로 깨진다(U4/U5가 훑은 "비교/정렬"
 * 경로나 커서 리더에는 없던, 이 writer만의 raw bind-path 사각지대 — `TimeTableJobTest`가 실제
 * 배치 실행으로 이를 처음 드러냈다). JPA 변환자와 똑같은 바이트 순서를 내는
 * [UuidBinaryCodec.encode]로 직접 인코딩해 `setBytes`로 바인딩한다.
 */
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
                    val identifier =
                        requireNotNull(
                            item.id,
                        ) { "TimeTableEntity.id must not be null at write time" }
                    ps.setBytes(ID_PLACE_NUMBER, UuidBinaryCodec.encode(identifier))
                    ps.setBytes(
                        RESTAURANT_ID_PLACE_NUMBER,
                        UuidBinaryCodec.encode(item.restaurantId),
                    )
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
