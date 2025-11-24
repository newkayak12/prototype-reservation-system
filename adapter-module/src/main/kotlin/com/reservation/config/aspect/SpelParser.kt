package com.reservation.config.aspect

import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component
import java.lang.reflect.Method

@Component
class SpelParser {
    private val parser = SpelExpressionParser()

    fun parse(
        expression: String,
        method: Method,
        args: Array<Any?>,
    ): String {
        val context =
            StandardEvaluationContext().apply {
                method.parameters.forEachIndexed { index, param ->
                    setVariable(param.name, args[index])
                }
            }
        return parser.parseExpression(expression).getValue(context) as String
    }
}
