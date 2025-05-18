package com.reservation.config.security.xss

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper

class RequestWrapper(request: HttpServletRequest?) : HttpServletRequestWrapper(request) {
    companion object {
        private const val LT = "<"
        private const val LT_REPLACEMENT = "&lt;"
        private const val GT = ">"
        private const val GT_REPLACEMENT = "&gt;"
        private const val OPEN_PAREN = "\\("
        private const val OPEN_PAREN_REPLACEMENT = "&#40;"
        private const val CLOSE_PAREN = "\\)"
        private const val CLOSE_PAREN_REPLACEMENT = "&#41;"
        private const val SINGLE_QUOTE = "'"
        private const val SINGLE_QUOTE_REPLACEMENT = "&#39;"
        private const val EVAL_PATTERN = "eval\\((.*)\\)"

        private const val JAVASCRIPT_PATTERN = "[\\\"\\\'][\\s]*javascript:(.*)[\\\"\\\']"
        private const val QUOTED_EMPTY_STRING = "\"\""

        private const val SCRIPT_PATTERN = "script"
        private const val EMPTY_STRING = ""
    }

    override fun getParameterValues(parameter: String): Array<String>? {
        val values = super.getParameterValues(parameter) ?: return null
        return Array<String>(values.size) {
                i ->
            manipulateRequest(values[i])
        }
    }

    override fun getParameter(name: String?): String? =
        super.getParameter(name) ?.let {
            manipulateRequest(it)
        }

    override fun getHeader(name: String?): String? =
        super.getHeader(name) ?.let {
            manipulateRequest(it)
        }

    private fun manipulateRequest(value: String): String {
        return value
            .replace(LT, LT_REPLACEMENT)
            .replace(GT, GT_REPLACEMENT)
            .replace(OPEN_PAREN, OPEN_PAREN_REPLACEMENT)
            .replace(CLOSE_PAREN, CLOSE_PAREN_REPLACEMENT)
            .replace(SINGLE_QUOTE, SINGLE_QUOTE_REPLACEMENT)
            .replace(EVAL_PATTERN, EMPTY_STRING)
            .replace(JAVASCRIPT_PATTERN, QUOTED_EMPTY_STRING)
            .replace(SCRIPT_PATTERN, EMPTY_STRING)
    }
}
