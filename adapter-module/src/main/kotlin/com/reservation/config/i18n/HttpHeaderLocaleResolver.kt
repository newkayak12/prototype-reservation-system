package com.reservation.config.i18n

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.LocaleResolver
import java.util.Locale

internal class HttpHeaderLocaleResolver : LocaleResolver {
    companion object {
        private val SUPPORTED_LANGUAGE = listOf(Locale.KOREAN, Locale.ENGLISH)
        private val DEFAULT_LANGUAGE = Locale.KOREAN
    }

    override fun resolveLocale(request: HttpServletRequest): Locale {
        val acceptLanguage =
            request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)
                ?.takeIf { it.isNotBlank() }
                ?: return DEFAULT_LANGUAGE

        val languageRanges = Locale.LanguageRange.parse(acceptLanguage)
        return Locale.lookup(languageRanges, SUPPORTED_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    @Suppress("EmptyFunctionBlock")
    override fun setLocale(
        request: HttpServletRequest,
        response: HttpServletResponse?,
        locale: Locale?,
    ) {
    }
}
