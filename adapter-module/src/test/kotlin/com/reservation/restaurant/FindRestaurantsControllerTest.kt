package com.reservation.restaurant

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.api.jqwik.JavaTypeArbitraryGenerator
import com.navercorp.fixturemonkey.api.jqwik.JqwikPlugin
import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.ninjasquad.springmockk.MockkBean
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Query
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.find.all.FindRestaurantsController
import com.reservation.rest.restaurant.request.FindRestaurantsRequest
import com.reservation.restaurant.port.input.FindRestaurantsUseCase
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import net.jqwik.api.Arbitraries
import net.jqwik.api.arbitraries.LongArbitrary
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.payload.JsonFieldType.ARRAY
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(FindRestaurantsController::class)
@ExtendWith(RestDocumentationExtension::class)
class FindRestaurantsControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var findRestaurantsUseCase: FindRestaurantsUseCase

    init {

        test("레스토랑을 조회했으나 결과 값이 존재하지 않는다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request =
                pureMonkey.giveMeBuilder<FindRestaurantsRequest>()
                    .set("size", 10L)
                    .sample()
            val useCaseResult = FindRestaurantsQueryResults(listOf(), false)
            val type = object : TypeReference<Map<String, Any>>() {}
            val convert = objectMapper.convertValue(request, type)
            val parameter: MultiValueMap<String, String> = LinkedMultiValueMap()
            for (key in convert.keys) {
                when (val value = convert[key]) {
                    is List<*> -> {
                        val list = value as List<Any>
                        list.forEach { parameter.add(key, it.toString()) }
                    }

                    is Set<*> -> {
                        val set = value as Set<Any>
                        set.forEach { parameter.add(key, it.toString()) }
                    }

                    null -> {
                        // Do Nothing
                    }

                    else -> {
                        parameter.add(key, value.toString())
                    }
                }
            }

            every {
                findRestaurantsUseCase.execute(any())
            } returns useCaseResult

            mockMvc.perform(
                get(RestaurantUrl.PREFIX).queryParams(parameter).header(
                    HttpHeaders.AUTHORIZATION,
                    CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                ).contentType(MediaType.APPLICATION_JSON_VALUE),
            ).andDo(MockMvcResultHandlers.print()).andExpect(status().is2xxSuccessful)
                .andExpect { jsonPath("$.list").isEmpty }
                .andExpect { jsonPath("$.hasNext").isBoolean }
                .andExpect { jsonPath("$.hasNext").value(false) }
        }

        test("Size=10 조건으로 레스토랑을 조회했고 결과 값이 10개 존재하며 다음 페이지가 존재한다.") {
            val pureMonkey =
                FixtureMonkeyFactory.giveMePureMonkey().plugin {
                    JqwikPlugin().javaTypeArbitraryGenerator(
                        object : JavaTypeArbitraryGenerator {
                            override fun longs(): LongArbitrary {
                                return Arbitraries.longs().greaterOrEqual(1)
                            }
                        },
                    )
                }.build()
            val request =
                pureMonkey.giveMeBuilder<FindRestaurantsRequest>().set("size", 10L).sample()
            val list = pureMonkey.giveMe<FindRestaurantsQueryResult>(10)
            val useCaseResult = FindRestaurantsQueryResults(list, false)
            val type = object : TypeReference<Map<String, Any>>() {}
            val convert = objectMapper.convertValue(request, type)
            val parameter: MultiValueMap<String, String> = LinkedMultiValueMap()
            for (key in convert.keys) {
                when (val value = convert[key]) {
                    is List<*> -> {
                        val list = value as List<Any>
                        list.forEach { parameter.add(key, it.toString()) }
                    }

                    is Set<*> -> {
                        val set = value as Set<Any>
                        set.forEach { parameter.add(key, it.toString()) }
                    }

                    null -> {
                        // Do Nothing
                    }

                    else -> {
                        parameter.add(key, value.toString())
                    }
                }
            }

            every {
                findRestaurantsUseCase.execute(any())
            } returns useCaseResult

            mockMvc.perform(
                get(RestaurantUrl.PREFIX).queryParams(parameter).header(
                    HttpHeaders.AUTHORIZATION,
                    CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                ).contentType(MediaType.APPLICATION_JSON_VALUE),
            ).andDo(MockMvcResultHandlers.print()).andExpect(status().is2xxSuccessful)
                .andExpect { jsonPath("$.list").isNotEmpty }
                .andExpect { jsonPath("$.list").isArray }
                .andExpect { jsonPath("$.list.size()").value(10) }
                .andExpect { jsonPath("$.hasNext").isBoolean }
                .andExpect { jsonPath("$.hasNext").value(true) }.andDo(
                    RestDocuments(
                        identifier = "findRestaurants",
                        documentTags = listOf("restaurant", "find", "all"),
                        summary = "레스토랑을 전체 조회한다.",
                        description = "조건을 바탕으로 레스토랑을 조회합니다.",
                        query =
                            arrayOf(
                                Query("identifierFrom", true, "페이징 위치 식별 값"),
                                Query("size", true, "페이지 사이즈"),
                                Query("searchText", true, "검색어"),
                                Query("tags", true, "태그"),
                                Query("nationalities", true, "국가"),
                                Query("cuisines", true, "요리"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("hasNext", BOOLEAN, false, "다음 페이지 존재 여부"),
                                Body("list[]", ARRAY, true, "레스토랑 리스트"),
                                Body("list[].identifier", STRING, false, "레스토랑 식별 값"),
                                Body("list[].information.name", STRING, false, "레스토랑 이름"),
                                Body("list[].information.introduce", STRING, true, "레스토랑 소개"),
                                Body("list[].address.zipCode", STRING, false, "우퍈번호"),
                                Body("list[].address.address", STRING, false, "주소"),
                                Body("list[].address.detail", STRING, true, "주소 상세"),
                                Body("list[].address.coordinate.latitude", NUMBER, false, "위도"),
                                Body("list[].address.coordinate.longitude", NUMBER, false, "경도"),
                                Body("list[].workingDays[]", ARRAY, true, "영업일"),
                                Body("list[].workingDays[].day", STRING, false, "요일"),
                                Body("list[].workingDays[].startTime", STRING, false, "시작일"),
                                Body("list[].workingDays[].endTime", STRING, false, "종료일"),
                                Body("list[].photos[]", ARRAY, true, "사진"),
                                Body("list[].photos[].url", STRING, false, "사진 URL"),
                                Body("list[].tags[]", ARRAY, false, "태그"),
                                Body("list[].tags[].id", NUMBER, false, "태그 식별 값"),
                                Body("list[].tags[].title", STRING, false, "태그 이름"),
                                Body("list[].tags[].categoryType", STRING, false, "태그 타입"),
                                Body("list[].nationalities[]", ARRAY, false, "국가"),
                                Body("list[].nationalities[].id", NUMBER, false, "국가 식별 값"),
                                Body("list[].nationalities[].title", STRING, false, "국가 이름"),
                                Body("list[].nationalities[].categoryType", STRING, false, "국가 타입"),
                                Body("list[].cuisines[]", ARRAY, false, "요리"),
                                Body("list[].cuisines[].id", NUMBER, false, "요리 식별 값"),
                                Body("list[].cuisines[].title", STRING, false, "요리 이름"),
                                Body("list[].cuisines[].categoryType", STRING, false, "요리 타입"),
                            ),
                    ).authorizedRequestHeader().create(),
                )
        }
    }
}
