package com.reservation.rest.internal.timetable.find

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.internal.timetable.TimeTableOccupancyUrl
import com.reservation.timetable.port.input.FindTimeTableAndOccupancyUseCase
import com.reservation.timetable.port.input.query.response.FindTimeTableAndOccupancyQueryResult
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.Arbitraries
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FindTimeTableOccupancyInternalControllerTest : FunSpec() {
    init {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var findTimeTableAndOccupancyUseCase: FindTimeTableAndOccupancyUseCase
        lateinit var mockMvc: MockMvc

        val jakartaMonkey = FixtureMonkeyFactory.giveMeJakartaMonkey().build()
        val timeTableIdSample = UuidGenerator.generate()
        val timeTableOccupancyIdSample = UuidGenerator.generate()
        val url = TimeTableOccupancyUrl.FIND_INTERNAL

        beforeTest { testCase ->
            findTimeTableAndOccupancyUseCase = mockk<FindTimeTableAndOccupancyUseCase>()
            val controller =
                FindTimeTableOccupancyInternalController(
                    findTimeTableAndOccupancyUseCase,
                )

            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("timeTableId의 값이 없을 때 NotFound가 된다.") {
            val emptyString = ""
            mockMvc.perform(
                get(url, emptyString, timeTableOccupancyIdSample),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isNotFound)
        }

        test("timeTableOccupancyId의 값이 없을 때 NotFound가 된다.") {
            val emptyString = ""
            mockMvc.perform(
                get(url, timeTableIdSample, emptyString),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isNotFound)
        }

        test("timeTableId의 값이 UUID 포맷과 맞지 않을 때 NotFound가 된다.") {
            val notUuidFormat = Arbitraries.strings().sample()
            mockMvc.perform(
                get(url, notUuidFormat, timeTableOccupancyIdSample),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isNotFound)
        }

        test("timeTableOccupancyId의 값이 UUID 포맷과 맞지 않을 때 NotFound가 된다.") {
            val notUuidFormat = Arbitraries.strings().sample()
            mockMvc.perform(
                get(url, timeTableIdSample, notUuidFormat),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isNotFound)
        }

        test("timeTableId, timeTableOccupancyId 값이 올바르고 결과를 반환한다.") {
            val result = jakartaMonkey.giveMeOne<FindTimeTableAndOccupancyQueryResult>()

            every {
                findTimeTableAndOccupancyUseCase.execute(any(), any())
            } returns result

            mockMvc.perform(
                get(url, timeTableIdSample, timeTableOccupancyIdSample)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is2xxSuccessful)
                .andExpect(jsonPath("$.table").isNotEmpty)
                .andExpect(jsonPath("$.book").isNotEmpty)
                .andDo(
                    RestDocuments(
                        identifier = "findTimeTableOccupancyInternally",
                        documentTags = listOf("zeroPayload", "timeTable", "timeTableOccupancy"),
                        summary = "예약 시간, 예약 정보",
                        description = "zeroPayload의 값을 조회하기 위한 API",
                        pathParameter =
                            arrayOf(
                                PathParameter("timeTableId", false, "시간표 PK"),
                                PathParameter("timeTableOccupancyId", false, "예약 PK"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("table.timeTableId", STRING, false, "시간표 PK"),
                                Body("table.restaurantId", STRING, false, "매장 PK"),
                                Body("table.date", STRING, false, "날짜"),
                                Body("table.day", STRING, false, "요일"),
                                Body("table.startTime", STRING, false, "예약 시작 시간"),
                                Body("table.endTime", STRING, false, "예약 종료 시간"),
                                Body("table.tableNumber", NUMBER, false, "테이블 번호"),
                                Body("table.tableSize", NUMBER, false, "테이블 사이즈"),
                                Body("book.timeTableOccupancyId", STRING, false, "예약 PK"),
                                Body("book.userId", STRING, false, "사용자 PK"),
                                Body("book.occupiedDatetime", STRING, false, "예약일"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    }
}
