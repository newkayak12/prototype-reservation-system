package com.reservation.user.self.usecase

import com.reservation.enumeration.JWTType.ACCESS_TOKEN
import com.reservation.enumeration.JWTType.REFRESH_TOKEN
import com.reservation.enumeration.JWTVersion
import com.reservation.enumeration.Role.USER
import com.reservation.exceptions.UnAuthorizedException
import com.reservation.utilities.generator.uuid.UuidGenerator
import com.reservation.utilities.provider.JWTProvider
import com.reservation.utilities.provider.JWTRecord
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RefreshGeneralUserUseCaseTest {
    private val expireTime = 300000L
    private val secret = "af869782f3abde439def4230f2b05bd701f1ab047ec80808d3b84f1c8923506a"
    private val issuer = "Test"
    private val version = JWTVersion.V1

    @SpyK
    private var tokenProvider =
        JWTProvider(
            secret = secret,
            duration = expireTime,
            issuer = issuer,
            version = version,
        )

    @InjectMockKs
    private lateinit var useCase: RefreshGeneralUserUseCase

    @DisplayName("[성공]")
    @Nested
    inner class Success {
        @DisplayName("토큰 디코딩에 성공하고 액세스 토큰을 재발급한다.")
        @Test
        fun `check valid refresh token and refresh access token`() {
            val record = JWTRecord(UuidGenerator.generate(), "test", USER)
            val refresh = tokenProvider.tokenize(record, REFRESH_TOKEN)

            val accessToken = useCase.refresh(refresh)

            assertThat(accessToken).isNotEmpty()
            assertThat(tokenProvider.validate(accessToken, ACCESS_TOKEN)).isTrue()
        }
    }

    @DisplayName("[실패]")
    @Nested
    inner class Failure {
        @DisplayName("refresh가 비어서 실패한다.")
        @Test
        fun `empty refresh token`() {
            val refresh = ""

            assertThrows<UnAuthorizedException> {
                useCase.refresh(refresh)
            }
        }

        @DisplayName("refresh가 잘못돼서 실패한다. - 잘못된 토큰")
        @Test
        fun `invalid refresh token - invalid`() {
            val record = JWTRecord(UuidGenerator.generate(), "test", USER)
            val refresh = tokenProvider.tokenize(record, REFRESH_TOKEN) + "1"

            assertThrows<UnAuthorizedException> {
                useCase.refresh(refresh)
            }
        }

        @DisplayName("refresh가 잘못돼서 실패한다. - refresh가 아니다.")
        @Test
        fun `invalid refresh token - not refresh`() {
            val record = JWTRecord(UuidGenerator.generate(), "test", USER)
            val refresh = tokenProvider.tokenize(record, ACCESS_TOKEN)

            assertThrows<UnAuthorizedException> {
                useCase.refresh(refresh)
            }
        }
    }
}
