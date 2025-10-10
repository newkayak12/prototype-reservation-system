package com.reservation.persistence.config

import com.p6spy.engine.common.ConnectionInformation
import com.p6spy.engine.event.JdbcEventListener
import com.p6spy.engine.logging.Category
import com.p6spy.engine.spy.P6SpyOptions
import com.p6spy.engine.spy.appender.MessageFormattingStrategy
import org.hibernate.engine.jdbc.internal.FormatStyle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.sql.SQLException

@Configuration
internal class P6SpyConfig {
    @Bean
    fun p6SpyEventListener(): P6SpyEventListener = P6SpyEventListener()
}

enum class QueryType(val value: String) {
    CREATE("create"),
    ALTER("alter"),
    DROP("drop"),
    COMMENT("comment"),
    STATEMENT(Category.STATEMENT.name),
}

internal class P6SpyEventListener : JdbcEventListener() {
    override fun onAfterGetConnection(
        connectionInformation: ConnectionInformation?,
        e: SQLException?,
    ) {
        P6SpyOptions.getActiveInstance().logMessageFormat = P6SpyFormatter::class.java.name
    }
}

internal class P6SpyFormatter : MessageFormattingStrategy {
    override fun formatMessage(
        connectionId: Int,
        now: String,
        elapsed: Long,
        category: String,
        prepared: String,
        sql: String,
        url: String,
    ): String =
        if (sql.isBlank()) {
            formatByCommand(category)
        } else {
            """
        ${formatBySql(sql, category)}
        ${getAdditionalMessages(elapsed)}
            """.trimMargin()
        }

    private fun formatByCommand(category: String): String =
        """
        ----------------------------------------------------------------------------------------------------
        Execute Command :
        $category
        ----------------------------------------------------------------------------------------------------
        """.trimIndent()

    private fun formatBySql(
        sql: String,
        category: String,
    ): String =
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

        Execution Time: $elapsed ms
        ----------------------------------------------------------------------------------------------------
        """.trimIndent()

    private fun isStatementDDL(
        sql: String,
        category: String,
    ): Boolean = isStatement(category) && isDDL(sql.trim { it <= ' ' }.lowercase())

    private fun isStatement(category: String): Boolean = QueryType.STATEMENT.value == category

    private fun isDDL(lowerSql: String): Boolean =
        lowerSql.startsWith(QueryType.CREATE.value) ||
            lowerSql.startsWith(QueryType.ALTER.value) ||
            lowerSql.startsWith(QueryType.DROP.value) ||
            lowerSql.startsWith(QueryType.COMMENT.value)
}
