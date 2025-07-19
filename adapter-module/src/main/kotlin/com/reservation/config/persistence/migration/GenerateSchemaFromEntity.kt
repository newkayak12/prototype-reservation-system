package com.reservation.config.persistence.migration

import com.reservation.config.persistence.migration.CreateDiff.DiffInformation
import com.reservation.config.persistence.migration.GenerateSchemaFromEntity.Companion.GENERATE
import com.reservation.config.persistence.migration.InitializeSkeema.SkeemaDirectory
import com.reservation.utilities.logger.loggerFactory
import kotlin.system.exitProcess
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile(value = [GENERATE])
class GenerateSchemaFromEntity : ApplicationRunner {
    private val log = loggerFactory<GenerateSchemaFromEntity>()

    companion object {
        const val GENERATE = "generate"
        const val PREVIOUS_PATH = GenerateSchemaFromFlyway.PATH
        const val INSTALL_DIRECTORY = "build/tools/skeema"
        const val DOWNLOAD_DIRECTORY = "build/skeema.tar.gz"
    }

    override fun run(args: ApplicationArguments?) {
        InitializeSkeema.install(SkeemaDirectory(INSTALL_DIRECTORY, DOWNLOAD_DIRECTORY))
        CreateDiff.createFlywayDiff(
            DiffInformation(
                INSTALL_DIRECTORY,
                PREVIOUS_PATH,
            ),
        )

        log.error("✅ schema.sql 생성 완료. 애플리케이션 종료합니다.")
        exitProcess(0)
    }
}
