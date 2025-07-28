package com.reservation.restaurant

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.ninjasquad.springmockk.MockkBean
import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Part
import com.reservation.config.restdoc.RequestPartFields
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.create.CreateRestaurantController
import com.reservation.rest.restaurant.request.CreateRestaurantRequest
import com.reservation.restaurant.port.input.CreateRestaurantUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockPart
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
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
@WebMvcTest(CreateRestaurantController::class)
@ExtendWith(RestDocumentationExtension::class)
class CreateRestaurantControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    lateinit var createRestaurantUseCase: CreateRestaurantUseCase

    @MockkBean
    lateinit var extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase

    private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

    private fun perfectCase() =
        pureMonkey.giveMeBuilder<CreateRestaurantRequest>()
            .setLazy("companyId") { Arbitraries.strings().numeric().ofLength(128).sample() }
            .setLazy("name") { Arbitraries.strings().numeric().ofLength(64).sample() }
            .setLazy("introduce") { Arbitraries.strings().ofLength(6000).sample() }
            .setLazy(
                "phone",
            ) { Arbitraries.strings().numeric().ofMinLength(11).ofMaxLength(13).sample() }
            .setLazy("zipCode") { Arbitraries.strings().numeric().ofLength(5).sample() }
            .setLazy("address") { Arbitraries.strings().numeric().ofLength(256).sample() }
            .setLazy("detail") { Arbitraries.strings().ofLength(256).sample() }
            .setLazy("latitude") { Arbitraries.bigDecimals().ofScale(6).sample() }
            .setLazy("longitude") { Arbitraries.bigDecimals().ofScale(6).sample() }
            .sample()

    init {
        test("비정상 요청으로 (JakartaValidation - NotBlank)인 케이스에 위배되어 실패한다.") {
            forAll(
                row("companyId"),
                row("name"),
                row("zipCode"),
                row("address"),
            ) { emptyField ->
                val requestBody =
                    when (emptyField) {
                        "companyId" -> perfectCase().copy(companyId = "")
                        "name" -> perfectCase().copy(name = "")
                        "zipCode" -> perfectCase().copy(zipCode = "")
                        "address" -> perfectCase().copy(address = "")
                        else -> perfectCase()
                    }

                val request =
                    MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                        .apply { headers.contentType = MediaType.APPLICATION_JSON }

                mockMvc.perform(
                    multipart(RestaurantUrl.CREATE_RESTAURANT)
                        .part(request)
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                        )
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                )
                    .andDo(MockMvcResultHandlers.print())
                    .andExpect(
                        status().is4xxClientError,
                    )
            }
        }

        test("비정상 요청으로 (JakartaValidation - Size)에 위배되어 실패한다.") {
            forAll(
                row("phone", 11, 13),
                row("address", 1, 256),
            ) { fieldName: String, min: Int, max: Int ->

                val value =
                    Arbitraries.oneOf(
                        Arbitraries.strings().numeric().ofMinLength(max + 1),
                        Arbitraries.strings().numeric().ofMaxLength(min - 1),
                    )
                        .sample()

                val requestBody =
                    when (fieldName) {
                        "phone" -> perfectCase().copy(phone = value)
                        "address" -> perfectCase().copy(address = value)
                        else -> perfectCase()
                    }

                val request =
                    MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                        .apply { headers.contentType = MediaType.APPLICATION_JSON }

                mockMvc.perform(
                    multipart(RestaurantUrl.CREATE_RESTAURANT)
                        .part(request)
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                        )
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                )
                    .andDo(MockMvcResultHandlers.print())
                    .andExpect(
                        status().is4xxClientError,
                    )
            }
        }

        test("비정상 요청으로 (JakartaValidation - Nullable이고 최대 Size)만 있는 경우 위배되어 실패한다.") {
            forAll(
                row("introduce", 6000),
                row("detail", 256),
            ) { fieldName: String, max: Int ->

                val requestBody =
                    when (fieldName) {
                        "introduce" ->
                            perfectCase().copy(
                                introduce = Arbitraries.strings().ofMinLength(max + 1).sample(),
                            )
                        "detail" ->
                            perfectCase().copy(
                                detail = Arbitraries.strings().ofMinLength(max + 1).sample(),
                            )
                        else -> perfectCase()
                    }

                val request =
                    MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                        .apply { headers.contentType = MediaType.APPLICATION_JSON }

                mockMvc.perform(
                    multipart(RestaurantUrl.CREATE_RESTAURANT)
                        .part(request)
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                        )
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                )
                    .andDo(MockMvcResultHandlers.print())
                    .andExpect(
                        status().is4xxClientError,
                    )
            }
        }

        test("정상 요청으로 정상 성공한다.") {
            val requestBody = perfectCase()
            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns Arbitraries.strings().sample()

            every {
                createRestaurantUseCase.execute(any())
            } returns true

            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(RestaurantUrl.CREATE_RESTAURANT)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.result").isBoolean)
                .andExpect(jsonPath("$.result").value(true))
        }

        test("이미지와 함께 정상 요청으로 정상 성공한다.") {
            val requestBody = perfectCase()
            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns Arbitraries.strings().sample()

            every {
                createRestaurantUseCase.execute(any())
            } returns true

            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }
            val photo1 =
                MockPart("photos", "img1.jpg", byteArrayOf(1, 2, 3))
                    .apply { headers.contentType = MediaType.IMAGE_JPEG }
            val photo2 =
                MockPart("photos", "img2.jpg", byteArrayOf(4, 5, 6))
                    .apply { headers.contentType = MediaType.IMAGE_JPEG }

            mockMvc.perform(
                multipart(RestaurantUrl.CREATE_RESTAURANT)
                    .part(request)
                    .part(photo1)
                    .part(photo2)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.result").isBoolean)
                .andExpect(jsonPath("$.result").value(true))
                .andDo(
                    RestDocuments(
                        identifier = "createRestaurant",
                        documentTags = listOf("restaurant", "create"),
                        summary = "음식점 생성",
                        description = "사용자 요청에 맞춰서 음식점을 생성합니다.",
                        requestPart =
                            arrayOf(
                                Part("photos", true, "사진"),
                                Part("request", false, "요청"),
                            ),
                        requestPartBody =
                            arrayOf(
                                RequestPartFields(
                                    partName = "request",
                                    jsonFields =
                                        arrayOf(
                                            Body(
                                                name = "companyId",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "회사 식별값",
                                            ),
                                            Body(
                                                name = "name",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "음식점 이름",
                                            ),
                                            Body(
                                                name = "introduce",
                                                jsonType = STRING,
                                                optional = true,
                                                description = "음식점 소개",
                                            ),
                                            Body(
                                                name = "phone",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "음식점 전화번호",
                                            ),
                                            Body(
                                                name = "zipCode",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "음식점 우편번호",
                                            ),
                                            Body(
                                                name = "address",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "음식점 주소",
                                            ),
                                            Body(
                                                name = "detail",
                                                jsonType = STRING,
                                                optional = true,
                                                description = "음식점 주소 상세",
                                            ),
                                            Body(
                                                name = "latitude",
                                                jsonType = NUMBER,
                                                optional = false,
                                                description = "위도",
                                            ),
                                            Body(
                                                name = "longitude",
                                                jsonType = NUMBER,
                                                optional = false,
                                                description = "경도",
                                            ),
                                            Body(
                                                name = "workingDays[]",
                                                jsonType = ARRAY,
                                                optional = true,
                                                description = "업무 시간",
                                            ),
                                            Body(
                                                name = "workingDays[].day",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "요일",
                                            ),
                                            Body(
                                                name = "workingDays[].startTime",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "시작 시간",
                                            ),
                                            Body(
                                                name = "workingDays[].endTime",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "마감 시간",
                                            ),
                                            Body(
                                                name = "tags[]",
                                                jsonType = ARRAY,
                                                optional = true,
                                                description = "태그",
                                            ),
                                            Body(
                                                name = "nationalities[]",
                                                jsonType = ARRAY,
                                                optional = true,
                                                description = "국가",
                                            ),
                                            Body(
                                                name = "cuisines[]",
                                                jsonType = ARRAY,
                                                optional = true,
                                                description = "음식",
                                            ),
                                        ),
                                ),
                            ),
                        responseBody =
                            arrayOf(
                                Body("result", BOOLEAN, true, "결과"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    }
}
