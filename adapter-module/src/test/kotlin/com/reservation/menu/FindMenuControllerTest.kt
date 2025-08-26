package com.reservation.menu

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.menu.port.input.FindMenuUseCase
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.find.one.FindMenuController
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import jakarta.validation.Validation
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.SpringValidatorAdapter

@ActiveProfiles(value = ["test"])
@ExtendWith(RestDocumentationExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FindMenuControllerTest : FunSpec(
    {

        val restDocsExtension = SpringRestDocsKotestExtension()

        extension(restDocsExtension)
        lateinit var mockMvc: MockMvc

        val url = "${MenuUrl.PREFIX}/{id}"

        beforeAny {
            val findMenuUseCase = mockk<FindMenuUseCase>()
            val controller = FindMenuController(findMenuUseCase)
            val validator = Validation.buildDefaultValidatorFactory().validator
            val springValidator = SpringValidatorAdapter(validator) // 수정

            val objectMapper = ObjectMapper()
            objectMapper.registerModule(JavaTimeModule())
            val converter = MappingJackson2HttpMessageConverter(objectMapper)

            mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                    .setMessageConverters(converter)
                    .apply {
                        MockMvcRestDocumentation.documentationConfiguration(
                            restDocsExtension.restDocumentation,
                        )
                    }
                    .setValidator(springValidator)
                    .build()
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
                .andExpect {
                    status().is4xxClientError
                }
        }
    },
)
