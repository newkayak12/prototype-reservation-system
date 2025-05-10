package com.example.config.persistence

import com.p6spy.engine.event.JdbcEventListener
import com.p6spy.engine.logging.Category
import com.p6spy.engine.spy.P6SpyOptions
import com.p6spy.engine.spy.appender.MessageFormattingStrategy
import org.hibernate.engine.jdbc.internal.FormatStyle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class P6SpyConfig {
    @Bean
    fun p6SpyEventListener(): P6SpyEventListener = P6SpyEventListener()
}

internal class P6SpyEventListener : JdbcEventListener() {
    init {
        P6SpyOptions.getActiveInstance().logMessageFormat = P6SpyFormatter::class.java.name
    }
}

internal class P6SpyFormatter : MessageFormattingStrategy {
    override fun formatMessage(
        connectionId: Int, now: String?, elapsed: Long, category: String?, prepared: String?, sql: String?, url: String?
    ): String = StringBuilder().apply {
        if (sql.isNullOrBlank()) {
            append(formatByCommand(category!!))
        } else {
            append(formatBySql(sql, category!!))
            append(getAdditionalMessages(elapsed))
        }
    }.toString()

    companion object {
        private const val CREATE: String = "create"
        private const val ALTER: String = "alter"
        private const val DROP: String = "drop"
        private const val COMMENT: String = "comment"
        private val STATEMENT = Category.STATEMENT.name


    }

    private fun formatByCommand(category: String): String =
        """
                ----------------------------------------------------------------------------------------------------
                Execute Command :
                $category
                ----------------------------------------------------------------------------------------------------
        """.trimIndent()

    private fun formatBySql(sql: String, category: String): String =
        if (isStatementDDL(sql, category)) {
            """
                        Execute DDL :${FormatStyle.DDL.formatter.format(sql)}
                        
                """.trimIndent()
        } else {
            """
                        Execute DML : ${FormatStyle.BASIC.formatter.format(sql)}
                        
                """.trimIndent()
        }

    private fun getAdditionalMessages(elapsed: Long): String =
        """

                Execution Time: ${elapsed} ms
                ----------------------------------------------------------------------------------------------------
            """.trimIndent()

    private fun isStatementDDL(sql: String, category: String): Boolean =
        isStatement(category) && isDDL(sql.trim { it <= ' ' }.lowercase())

    private fun isStatement(category: String): Boolean = STATEMENT == category

    private fun isDDL(lowerSql: String): Boolean =
        lowerSql.startsWith(P6SpyFormatter.CREATE)
                || lowerSql.startsWith(P6SpyFormatter.ALTER)
                || lowerSql.startsWith(P6SpyFormatter.DROP)
                || lowerSql.startsWith(P6SpyFormatter.COMMENT)
}