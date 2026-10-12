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
class P6SpyConfig {
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

class P6SpyEventListener : JdbcEventListener() {
    override fun onAfterGetConnection(
        connectionInformation: ConnectionInformation?,
        e: SQLException?,
    ) {
        P6SpyOptions.getActiveInstance().logMessageFormat = P6SpyFormatter::class.java.name
    }
}

class P6SpyFormatter : MessageFormattingStrategy {
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
            Execute DDL :${safeFormat(FormatStyle.DDL, sql)}

            """.trimIndent()
        } else {
            """
            Execute DML : ${safeFormat(FormatStyle.BASIC, sql)}

            """.trimIndent()
        }

    /**
     * binary-pk 회귀 수정 (2026-10-06): `V1_22`/`V1_23` 마이그레이션이 추가한
     * `CREATE PROCEDURE ... BEGIN ... END` 블록(본문에 `PREPARE`/`EXECUTE`, `CONCAT(...)`,
     * `IF(...)` 등 중첩 괄호가 여럿 있다)을 Hibernate의 `BasicFormatterImpl`(DDL 포매터)에
     * 넣으면 괄호 스택 처리가 깨져 `NoSuchElementException`을 던진다 — 이 예외가 잡히지 않은
     * 채 p6spy 로깅 경로를 타고 올라가 Flyway의 `migrate()` 호출 자체를 실패시키고,
     * 결과적으로 `entityManagerFactory` 빈 생성이 통째로 깨진다(`ReservationApplicationTest`가
     * 실제 전체 체인을 Flyway로 적용하면서 처음 드러냈다). 이 포매터는 **로그 가독성을 위한
     * best-effort 가공**일 뿐 — 포맷팅이 실패해도 원본 SQL을 그대로 보여주는 것으로
     * 충분하다. 로깅 포매터의 실패가 애플리케이션 부팅/배치 실행을 막아서는 안 된다.
     */
    private fun safeFormat(
        style: FormatStyle,
        sql: String,
    ): String = runCatching { style.formatter.format(sql) }.getOrDefault(sql)

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
