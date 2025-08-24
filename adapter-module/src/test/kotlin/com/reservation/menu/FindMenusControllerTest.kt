package com.reservation.menu

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMe
import com.ninjasquad.springmockk.MockkBean
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.port.input.FindMenusUseCase
import com.reservation.menu.port.input.response.FindMenusQueryResult
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.find.FindMenusController
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType.ARRAY
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(FindMenusController::class)
@ExtendWith(RestDocumentationExtension::class)
class FindMenusControllerTest(
    private val objectMapper: ObjectMapper,
    private val mockMvc: MockMvc,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var findMenusUseCase: FindMenusUseCase

    val id = "${MenuUrl.PREFIX}/{id}/all"

    init {

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
    }
}
