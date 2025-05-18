package com.reservation.config.security

import com.reservation.config.security.jwt.JwtFilter
import com.reservation.config.security.jwt.JwtProvider
import com.reservation.config.security.jwt.SecurityRole
import com.reservation.config.security.jwt.properties.JwtProperties
import com.reservation.config.security.jwt.properties.JwtWhitelist
import com.reservation.config.security.xss.CrossSiteScriptFilter
import com.reservation.config.security.xss.properties.XssBlacklist
import com.reservation.encoder.password.PasswordEncoderUtility
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.savedrequest.RequestCacheAwareFilter

@EnableWebSecurity
@Profile(value = ["temporary", "local", "stage", "production"])
class SecurityConfig(
    private val jwtPath: JwtWhitelist,
    private val xssPath: XssBlacklist,
    private val customAccessDeniedHandler: AccessDeniedHandler,
    private val customAuthenticationEntryPoint: AuthenticationEntryPoint,
    private val jwtProperties: JwtProperties,
    private val provider: JwtProvider = JwtProvider(jwtProperties),
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderUtility.getInstance()

    @Bean
    fun authorityMapper(): GrantedAuthoritiesMapper =
        GrantedAuthoritiesMapper { authorities ->
            val mapped = authorities.toMutableList()
            if (authorities.any { it.authority == SecurityRole.ROLE_ADMIN.name }) {
                mapped.add(SecurityRole.ROLE_MANAGER)
            }
            if (authorities.any { it.authority == SecurityRole.ROLE_MANAGER.name }) {
                mapped.add(SecurityRole.ROLE_USER)
            }
            mapped
        }

    @Bean
    fun filterChain(httpSecurity: HttpSecurity) {
        httpSecurity
            .cors { it.disable() }
            .exceptionHandling { it.disable() }
            .exceptionHandling {
                it.authenticationEntryPoint(customAuthenticationEntryPoint)
                it.accessDeniedHandler(customAccessDeniedHandler)
            }
            .headers { it.frameOptions { it.sameOrigin() } }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/**").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(
                JwtFilter(provider, jwtPath),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .addFilterAfter(CrossSiteScriptFilter(xssPath), RequestCacheAwareFilter::class.java)
            .build()
    }
}
