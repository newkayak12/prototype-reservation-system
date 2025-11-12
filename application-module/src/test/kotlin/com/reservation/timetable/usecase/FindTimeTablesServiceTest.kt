package com.reservation.timetable.usecase

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.timetable.port.input.query.request.FindTimeTableQuery
import com.reservation.timetable.port.output.FindTimeTables
import com.reservation.timetable.port.output.FindTimeTables.FindTimeTableResult
import io.kotest.matchers.ints.exactly
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@DisplayName("시간표 리스트를 조건에 맞춰서 조회한다.")
@ExtendWith(MockKExtension::class)
class FindTimeTablesServiceTest {

    @MockK
    private lateinit var findTimeTables: FindTimeTables

    @InjectMockKs
    private lateinit var service: FindTimeTablesService


    @DisplayName("조건에 맞춰 조회한 결과, 결과 값이 없어")
    @Nested
    inner class `Empty results` {
        @DisplayName("결과적으로 조회 내용이 비어 있다.")
        @Test
        fun `return empty list`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val list = pureMonkey.giveMe<FindTimeTableResult>(0)
            val query = pureMonkey.giveMeOne<FindTimeTableQuery>()

            every {
                findTimeTables.query(any())
            } returns list

            val results = service.execute(query)

            assertThat(results).isEmpty()
            verify(exactly = 1) { findTimeTables.query(any()) }
        }
    }

    @DisplayName("조건에 맞춰 조회한 결과, 결과 값 30건이 나온다")
    @Nested
    inner class `Exists results` {
        @DisplayName("결과적으로 조회 내용이 30 건이 있다.")
        @Test
        fun `return 30 list`() {
            val size = 30
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val list = pureMonkey.giveMe<FindTimeTableResult>(size)
            val query = pureMonkey.giveMeOne<FindTimeTableQuery>()

            every {
                findTimeTables.query(any())
            } returns list

            val results = service.execute(query)

            assertThat(results).isNotEmpty().hasSize(size)
            verify(exactly = 1) { findTimeTables.query(any()) }
        }
    }
}
