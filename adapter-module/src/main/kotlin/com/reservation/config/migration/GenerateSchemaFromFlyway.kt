package com.reservation.config.migration

import com.reservation.config.migration.GenerateSchemaFromFlyway.Companion.SCHEMA
import com.reservation.utilities.logger.loggerFactory
import kotlin.system.exitProcess
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@Profile(value = [SCHEMA])
class GenerateSchemaFromFlyway(
    private val dataSource: DataSource,
) : ApplicationRunner {
    private val log = loggerFactory<GenerateSchemaFromFlyway>()

    companion object {
        const val SCHEMA = "schema"
        const val PATH = "build/generated/previous-schema.sql"
    }

    override fun run(args: ApplicationArguments?) {
        ExtractDDL.delete(PATH)
        ExtractDDL.extract(dataSource, PATH)
        log.error("✅ prev-schema.sql 생성 완료. 애플리케이션 종료합니다.")
        exitProcess(0)
    }
}
