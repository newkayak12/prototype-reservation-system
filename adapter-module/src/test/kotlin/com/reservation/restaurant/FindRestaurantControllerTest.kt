package com.reservation.restaurant

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.ninjasquad.springmockk.MockkBean
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.find.one.FindRestaurantController
import com.reservation.restaurant.port.input.FindRestaurantUseCase
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.payload.JsonFieldType.ARRAY
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(FindRestaurantController::class)
@ExtendWith(RestDocumentationExtension::class)
class FindRestaurantControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var findRestaurantUseCase: FindRestaurantUseCase
    private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

    init {

        test("id로 조회했으나 레스토랑이 존재하지 않는다. 따라서 NoSuchPersistedElementException가 발생한다. ") {
            val uuid = UuidGenerator.generate()

            every {
                findRestaurantUseCase.execute(any())
            } throws NoSuchPersistedElementException()

            mockMvc.perform(
                get("${RestaurantUrl.PREFIX}/$uuid")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.APPLICATION_JSON_VALUE),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("id로 조회했고 레스토랑이 존재한다.") {
            val uuid = UuidGenerator.generate()
            val result = pureMonkey.giveMeOne<FindRestaurantQueryResult>()
            val url = "${RestaurantUrl.PREFIX}/{id}"
            every {
                findRestaurantUseCase.execute(any())
            } returns result

            mockMvc.perform(
                get(url, uuid)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.APPLICATION_JSON_VALUE),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is2xxSuccessful)
                .andDo(
                    RestDocuments(
                        identifier = "findRestaurant",
                        documentTags = listOf("restaurant", "find", "one"),
                        summary = "레스토랑 하나를 조회합니다.",
                        description = "ID를 바탕으로 레스토랑 하나를 조회합니다.",
                        pathParameter =
                            arrayOf(
                                PathParameter(
                                    name = "id",
                                    optional = false,
                                    description = "식별 값",
                                ),
                            ),
                        responseBody =
                            arrayOf(
                                Body(
                                    name = "identifier",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "식별값",
                                ),
                                Body(
                                    name = "company.companyId",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "회사 식별값",
                                ),
                                Body(
                                    name = "company.companyName",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "회사 이름",
                                ),
                                Body(
                                    name = "userId",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "매장 주인 식별값",
                                ),
                                Body(
                                    name = "information.name",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "매장명",
                                ),
                                Body(
                                    name = "information.introduce",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "매장 설명",
                                ),
                                Body(
                                    name = "phone",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "전화번호",
                                ),
                                Body(
                                    name = "address.zipCode",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "우편번호",
                                ),
                                Body(
                                    name = "address.address",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "주소",
                                ),
                                Body(
                                    name = "address.detail",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "주소 상세",
                                ),
                                Body(
                                    name = "address.coordinate.latitude",
                                    jsonType = NUMBER,
                                    optional = false,
                                    description = "위도",
                                ),
                                Body(
                                    name = "address.coordinate.longitude",
                                    jsonType = NUMBER,
                                    optional = false,
                                    description = "경도",
                                ),
                                Body(
                                    name = "workingDays[]",
                                    jsonType = ARRAY,
                                    optional = false,
                                    description = "영업일",
                                ),
                                Body(
                                    name = "workingDays[].day",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "요일",
                                ),
                                Body(
                                    name = "workingDays[].startTime",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "시작 시간",
                                ),
                                Body(
                                    name = "workingDays[].endTime",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "종료 시간",
                                ),
                                Body(
                                    name = "photos[]",
                                    jsonType = ARRAY,
                                    optional = false,
                                    description = "사진",
                                ),
                                Body(
                                    name = "photos[].url",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "사진 URL",
                                ),
                                Body(
                                    name = "tags[]",
                                    jsonType = ARRAY,
                                    optional = false,
                                    description = "태그",
                                ),
                                Body(
                                    name = "tags[].id",
                                    jsonType = NUMBER,
                                    optional = true,
                                    description = "태그 식별값",
                                ),
                                Body(
                                    name = "tags[].title",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "태그 이름",
                                ),
                                Body(
                                    name = "tags[].categoryType",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "태그 타입",
                                ),
                                Body(
                                    name = "nationalities[]",
                                    jsonType = ARRAY,
                                    optional = false,
                                    description = "국가",
                                ),
                                Body(
                                    name = "nationalities[].id",
                                    jsonType = NUMBER,
                                    optional = true,
                                    description = "국가 식별값",
                                ),
                                Body(
                                    name = "nationalities[].title",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "국가 이름",
                                ),
                                Body(
                                    name = "nationalities[].categoryType",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "국가 타입",
                                ),
                                Body(
                                    name = "cuisines[]",
                                    jsonType = ARRAY,
                                    optional = false,
                                    description = "요리",
                                ),
                                Body(
                                    name = "cuisines[].id",
                                    jsonType = NUMBER,
                                    optional = true,
                                    description = "요리 식별값",
                                ),
                                Body(
                                    name = "cuisines[].title",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "요리 이름",
                                ),
                                Body(
                                    name = "cuisines[].categoryType",
                                    jsonType = STRING,
                                    optional = true,
                                    description = "요리 타입",
                                ),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    }
}
