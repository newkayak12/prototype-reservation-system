package com.reservation.rest.timetable.occupancy.create

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.timetable.port.input.CreateTimeTableOccupancyUseCase
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import org.springframework.test.web.servlet.MockMvc
import java.time.format.DateTimeFormatter

class CreateTimeTableOccupancyControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var createTimeTableOccupancyUseCase: CreateTimeTableOccupancyUseCase
        lateinit var extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase

        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        beforeTest { testCase ->
            createTimeTableOccupancyUseCase = mockk<CreateTimeTableOccupancyUseCase>()
            extractIdentifierFromHeaderUseCase = mockk<ExtractIdentifierFromHeaderUseCase>()
            val controller =
                CreateTimeTableOccupancyController(
                    createTimeTableOccupancyUseCase,
                    extractIdentifierFromHeaderUseCase,
                )

            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }
    },
) {
    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ISO_DATE
        private val TIME_FORMAT = DateTimeFormatter.ISO_TIME
    }
}
