package com.reservation.menu

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.port.input.FindMenuUseCase
import com.reservation.menu.port.input.response.FindMenuQueryResult
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.find.one.FindMenuController
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType.ARRAY
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FindMenuControllerTest : FunSpec(
    {

        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var findMenuUseCase: FindMenuUseCase
        val url = "${MenuUrl.PREFIX}/{id}"

        beforeAny {
            findMenuUseCase = mockk<FindMenuUseCase>()
            val controller = FindMenuController(findMenuUseCase)
            mockMvc = MockMvcFactory.buildMockMvc(controller, restDocsExtension.restDocumentation)
        }

        test("올바르지 않은 id 형식으로 4xxClientError가 발생한다.") {
            mockMvc.perform(
                get(url, "가나다")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect { status().is4xxClientError }
                .andExpect { status().isNotFound }
        }

        test("올바른 id 형식으로 조회했지만 결과가 없어 4xxClientError가 발생한다.") {
            val id = UuidGenerator.generate()

            every { findMenuUseCase.execute(any()) } throws NoSuchPersistedElementException()

            mockMvc.perform(
                get(url, id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect { status().is4xxClientError }
                .andExpect { status().isBadRequest }
        }

        test("올바른 id 형식으로 조회에 성공하고 결과를 반환한다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val id = UuidGenerator.generate()
            val queryResult = pureMonkey.giveMeOne<FindMenuQueryResult>()

            every { findMenuUseCase.execute(any()) } returns queryResult

            mockMvc.perform(
                get(url, id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect { status().is2xxSuccessful }
                .andExpect { status().isOk }
                .andDo(
                    RestDocuments(
                        identifier = "findMenu",
                        documentTags = listOf("menu", "find", "one"),
                        summary = "메뉴 단일 조회",
                        description = "메뉴 식별 값으로 메뉴를 조회합니다.",
                        pathParameter =
                            arrayOf(
                                PathParameter("id", false, "메뉴 식별값"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("id", STRING, false, "메뉴 식별 값"),
                                Body("restaurantId", STRING, false, "음식점 식별 값"),
                                Body("title", STRING, false, "메뉴 이름"),
                                Body("description", STRING, false, "메뉴 설명"),
                                Body("price", NUMBER, false, "메뉴 가격"),
                                Body("representative", BOOLEAN, false, "대표 메뉴 여부"),
                                Body("recommended", BOOLEAN, false, "추천 메뉴 여부"),
                                Body("visible", BOOLEAN, false, "노출 여부"),
                                Body("photos[]", ARRAY, true, "사진"),
                                Body("photos[].id", STRING, false, "사진 식별 값"),
                                Body("photos[].url", STRING, false, "사진 url"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
