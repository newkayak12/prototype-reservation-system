package com.reservation.config.aspect

import org.springframework.context.expression.MethodBasedEvaluationContext
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.stereotype.Component
import java.lang.reflect.Method

@Component
@SuppressWarnings("TooGenericExceptionCaught")
class SpelParser {
    private val parser = SpelExpressionParser()

    fun parse(
        expression: String,
        method: Method,
        args: Array<Any?>,
    ): String =
        try {
            val context =
                MethodBasedEvaluationContext(
                    null,
                    method,
                    args,
                    DefaultParameterNameDiscoverer(),
                )

            parser.parseExpression(expression).getValue(context) as? String ?: expression
        } catch (_: Exception) {
            expression // 평가 실패하면 원본 문자열 반환
        }
}
