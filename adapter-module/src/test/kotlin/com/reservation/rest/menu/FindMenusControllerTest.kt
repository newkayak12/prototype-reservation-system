package com.reservation.rest.menu

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.port.input.FindMenusUseCase
import com.reservation.menu.port.input.response.FindMenusQueryResult
import com.reservation.rest.menu.find.all.FindMenusController
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FindMenusControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var findMenusUseCase: FindMenusUseCase

        beforeTest { testCase ->
            findMenusUseCase = mockk<FindMenusUseCase>()
            val controller = FindMenusController(findMenusUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        val id = "${MenuUrl.PREFIX}/{id}/all"

        test("메뉴가 없는 레스토랑의 메뉴를 조회하고 결과는 0건이다.") {
            mockMvc.perform(
                get(id, "가나다")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("메뉴가 14개 있는 레스토랑의 메뉴를 조회하고 결과는 14건이다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val uuid = UuidGenerator.generate()
            val size = 14
            val list = pureMonkey.giveMe<FindMenusQueryResult>(size)
            every {
                findMenusUseCase.execute(uuid)
            } returns list

            mockMvc.perform(
                get(id, uuid)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is2xxSuccessful)
                .andExpect(jsonPath("$.list").isNotEmpty)
                .andExpect(jsonPath("$.list").isArray)
                .andDo(
                    RestDocuments(
                        identifier = "findMenus",
                        documentTags = listOf("menu", "find", "all"),
                        summary = "음식점에 있는 메뉴 전체를 조회한다.",
                        description = "음식점의 전 메뉴 조회",
                        pathParameter =
                            arrayOf(
                                PathParameter("id", false, "음식점 식별자"),
                            ),
                        responseBody =
                            arrayOf(
                                Body(
                                    name = "list[]",
                                    jsonType = ARRAY,
                                    optional = true,
                                    description = "메뉴 list",
                                ),
                                Body(
                                    name = "list[].id",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "메뉴 식별자",
                                ),
                                Body(
                                    name = "list[].restaurantId",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "음식점 식별자",
                                ),
                                Body(
                                    name = "list[].title",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "메뉴 이름",
                                ),
                                Body(
                                    name = "list[].description",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "메뉴 설명",
                                ),
                                Body(
                                    name = "list[].price",
                                    jsonType = NUMBER,
                                    optional = false,
                                    description = "메뉴 가격",
                                ),
                                Body(
                                    name = "list[].isRepresentative",
                                    jsonType = BOOLEAN,
                                    optional = false,
                                    description = "메뉴 대표 메뉴 여부",
                                ),
                                Body(
                                    name = "list[].isRecommended",
                                    jsonType = BOOLEAN,
                                    optional = false,
                                    description = "메뉴 추천 여부",
                                ),
                                Body(
                                    name = "list[].isVisible",
                                    jsonType = BOOLEAN,
                                    optional = false,
                                    description = "메뉴 노출 여부",
                                ),
                                Body(
                                    "list[].photos[]",
                                    ARRAY,
                                    true,
                                    "메뉴 사진",
                                ),
                                Body(
                                    "list[].photos[].id",
                                    STRING,
                                    false,
                                    "메뉴 사진 식별자",
                                ),
                                Body(
                                    "list[].photos[].url",
                                    STRING,
                                    false,
                                    "메뉴 사진 url",
                                ),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
