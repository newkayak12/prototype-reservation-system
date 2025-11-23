package com.reservation.rest.timetable.find

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Query
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.timetable.TimeTableUrl
import com.reservation.timetable.port.input.FindTimeTablesUseCase
import com.reservation.timetable.port.input.query.response.FindTimeTableQueryResult
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FindTimeTableControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var findTimeTablesUseCase: FindTimeTablesUseCase

        val dateTimeFormat = DateTimeFormatter.ISO_DATE
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        beforeTest { testCase ->
            findTimeTablesUseCase = mockk<FindTimeTablesUseCase>()
            val controller = FindTimeTableController(findTimeTablesUseCase)

            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("조건에 맞춰 조회를 진행했으나 결과가 없어 빈 리스트가 반환된다.") {

            every {
                findTimeTablesUseCase.execute(any())
            } returns emptyList()

            mockMvc.perform(
                get(TimeTableUrl.FINDS)
                    .queryParam("restaurantId", UuidGenerator.generate())
                    .queryParam("date", dateTimeFormat.format(LocalDate.now()))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.APPLICATION_JSON_VALUE),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is2xxSuccessful)
                .andExpect(jsonPath("$.list").isEmpty)
        }

        test("조건에 맞춰 조회를 진행했고 32건의 결과가 있어 32개의 리스트가 반환된다.") {
            val size = 32
            val queryList = pureMonkey.giveMe<FindTimeTableQueryResult>(size)

            every {
                findTimeTablesUseCase.execute(any())
            } returns queryList

            mockMvc.perform(
                get(TimeTableUrl.FINDS)
                    .queryParam("restaurantId", UuidGenerator.generate())
                    .queryParam("date", dateTimeFormat.format(LocalDate.now()))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.APPLICATION_JSON_VALUE),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is2xxSuccessful)
                .andExpect(jsonPath("$.list").isArray)
                .andExpect(jsonPath("$.list").isNotEmpty)
                .andExpect(jsonPath("$.list.size()").value(size))
                .andDo(
                    RestDocuments(
                        identifier = "findTimeTables",
                        documentTags =
                            listOf(
                                "timetable",
                                "find",
                            ),
                        summary = "시간표 조회",
                        description = "매장의 특정일의 시간표를 조회합니다.",
                        query =
                            arrayOf(
                                Query("restaurantId", false, "매장 id"),
                                Query("date", false, "조회 날짜"),
                                Query("tableStatus", true, "스케쥴 상태, 기본 값 EMPTY"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("list[].id", STRING, false, "시간표 ID"),
                                Body("list[].restaurantId", STRING, false, "매장 ID"),
                                Body("list[].date", STRING, false, "날짜"),
                                Body("list[].day", STRING, false, "요일"),
                                Body("list[].startTime", STRING, false, "시작 시간"),
                                Body("list[].endTime", STRING, false, "종료 시간"),
                                Body("list[].tableNumber", NUMBER, false, "테이블 번호"),
                                Body("list[].tableSize", NUMBER, false, "테이블 수용 인원"),
                                Body("list[].tableStatus", STRING, false, "테이블 상태"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
