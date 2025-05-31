package com.reservation.config.security

import com.reservation.config.security.jwt.JwtFilter
import com.reservation.config.security.jwt.properties.JwtWhitelist
import com.reservation.config.security.xss.CrossSiteScriptFilter
import com.reservation.config.security.xss.properties.XssBlacklist
import com.reservation.enumeration.SecurityRole
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import com.reservation.utilities.provider.JWTProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.savedrequest.RequestCacheAwareFilter

@EnableWebSecurity
@Profile(value = ["temporary", "local", "stage", "production"])
@Configuration
class SecurityConfig(
    private val jwtPath: JwtWhitelist,
    private val xssPath: XssBlacklist,
    private val customAccessDeniedHandler: AccessDeniedHandler,
    private val customAuthenticationEntryPoint: AuthenticationEntryPoint,
    private var jwtProvider: JWTProvider,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderUtility.getInstance()

    @Bean
    fun authorityMapper(): GrantedAuthoritiesMapper =
        GrantedAuthoritiesMapper { authorities ->
            val mapped = authorities.toMutableList()
            if (authorities.any { it.authority == SecurityRole.ROLE_ADMIN.name }) {
                mapped.add(SimpleGrantedAuthority(SecurityRole.ROLE_MANAGER.name))
            }
            if (authorities.any { it.authority == SecurityRole.ROLE_MANAGER.name }) {
                mapped.add(SimpleGrantedAuthority(SecurityRole.ROLE_USER.name))
            }
            mapped
        }

    @Bean
    fun filterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        return httpSecurity
            .cors { it.disable() }
            .exceptionHandling { it.disable() }
            .exceptionHandling {
                it.authenticationEntryPoint(customAuthenticationEntryPoint)
                it.accessDeniedHandler(customAccessDeniedHandler)
            }
            .headers { it.frameOptions { i -> i.sameOrigin() } }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/**").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(
                JwtFilter(jwtProvider, jwtPath),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .addFilterAfter(CrossSiteScriptFilter(xssPath), RequestCacheAwareFilter::class.java)
            .build()
    }
}
