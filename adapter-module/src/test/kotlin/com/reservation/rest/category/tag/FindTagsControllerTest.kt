package com.reservation.rest.category.tag

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.reservation.category.tag.port.input.FindTagsByTitleUseCase
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Query
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.category.CategoryUrl
import com.reservation.rest.category.tags.FindTagsController
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FindTagsControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var findNationalitiesQuery: FindTagsByTitleUseCase

        beforeTest { testCase ->
            findNationalitiesQuery = mockk<FindTagsByTitleUseCase>()
            val controller = FindTagsController(findNationalitiesQuery)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("태크 카테고리를를 조회하여 총 18건의 결과가 노출된다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            every {
                findNationalitiesQuery.execute(any())
            } returns pureMonkey.giveMe(18)

            mockMvc.perform(
                get(CategoryUrl.TAGS)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.list").isArray,
                    jsonPath("$.list").isNotEmpty,
                    jsonPath("$.list[*]").isNotEmpty,
                )
                .andDo(
                    RestDocuments(
                        identifier = "findTags",
                        documentTags = listOf("category", "tag"),
                        summary = "태그 카테고리 조회",
                        description = "태그 카테고리를 title로 전체 조회합니다.",
                        query =
                            arrayOf(
                                Query("title", true, "카테고리 명"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("list[].id", NUMBER, true, "카테고리 번호"),
                                Body("list[].title", STRING, true, "카테고리 title"),
                                Body("list[].categoryType", STRING, true, "태그 카테고리"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
