package com.reservation.menu

import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.menu.port.input.FindMenuUseCase
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.find.one.FindMenuController
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
            mockMvc = MockMvcFactory.buildMockMvc(controller)

            val objectMapper = MockMvcFactory.objectMapper
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
