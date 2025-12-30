package com.reservation.timetable.usecase

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.timetable.port.output.FindTimeTableAndOccupancy
import com.reservation.timetable.port.output.FindTimeTableAndOccupancy.FindTimeTableAndOccupancyResult
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@DisplayName("Event가 발행되어 전달받은 키 값으로 ")
@ExtendWith(MockKExtension::class)
class FindTimeTableAndOccupancyServiceTest {
    @MockK
    private lateinit var findTimeTableAndOccupancy: FindTimeTableAndOccupancy

    @InjectMockKs
    private lateinit var service: FindTimeTableAndOccupancyService

    private lateinit var pureMonkey: FixtureMonkey

    @BeforeEach
    fun setUp() {
        pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
    }

    @DisplayName("TimeTable과 TimeTableOccupancy를 조회하고")
    @Nested
    inner class NoResult {
        @DisplayName("결과가 없어 NoSuchPersistedElementException가 발생한다")
        @Test
        fun `no result founded and throws NoSuchPersistedElementException`() {
            val timeTableId = UuidGenerator.generate()
            val timeTableOccupancyId = UuidGenerator.generate()

            every {
                findTimeTableAndOccupancy.query(any())
            } throws NoSuchPersistedElementException()

            shouldThrow<NoSuchPersistedElementException> {
                service.execute(timeTableId, timeTableOccupancyId)
            }
        }
    }

    @DisplayName("TimeTable과 TimeTableOccupancy를 조회하고")
    @Nested
    inner class ResultFound {
        @DisplayName("결과를 찾아 리턴한다.")
        @Test
        fun `result founded and returned`() {
            val queryResult = pureMonkey.giveMeOne<FindTimeTableAndOccupancyResult>()
            val timeTableId = UuidGenerator.generate()
            val timeTableOccupancyId = UuidGenerator.generate()

            every {
                findTimeTableAndOccupancy.query(any())
            } returns queryResult

            val result = service.execute(timeTableId, timeTableOccupancyId)

            assertThat(result)
                .usingRecursiveComparison()
                .ignoringAllOverriddenEquals()
                .isEqualTo(queryResult)
        }
    }
}
