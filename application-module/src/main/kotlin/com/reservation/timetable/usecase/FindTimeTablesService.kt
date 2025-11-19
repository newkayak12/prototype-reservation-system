package com.reservation.timetable.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.timetable.port.input.FindTimeTablesUseCase
import com.reservation.timetable.port.input.query.request.FindTimeTableQuery
import com.reservation.timetable.port.input.query.response.FindTimeTableQueryResult
import com.reservation.timetable.port.output.FindTimeTables
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindTimeTablesService(
    private val findTimeTables: FindTimeTables,
) : FindTimeTablesUseCase {
    @Transactional(readOnly = true)
    override fun execute(query: FindTimeTableQuery): List<FindTimeTableQueryResult> =
        findTimeTables.query(query.toInquiry()).map { it.toQuery() }
}
