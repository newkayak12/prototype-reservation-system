package com.reservation.config.persistence.migration

import com.reservation.utilities.logger.loggerFactory
import java.io.File

object CreateDiff {
    private val log = loggerFactory<CreateDiff>()
    private const val WAIT_THREE_MINUTES = 3000L

    data class DiffInformation(
        val skeemaDirectory: String,
        val prevSqlPath: String,
    )

    private fun skeemaPull(
        skeemaDirectory: String,
        prevSqlPath: String,
    ) {
        val skeema = File(skeemaDirectory).absolutePath
        val previous = File(prevSqlPath).absolutePath

        log.error("$skeema/skeema pull $previous")

        ProcessBuilder("$skeema/skeema", "pull", "$previous").start()
    }

    fun createFlywayDiff(diff: DiffInformation) {
        val before = File(diff.prevSqlPath).listFiles()
        skeemaPull(diff.skeemaDirectory, diff.prevSqlPath)

        Thread.sleep(WAIT_THREE_MINUTES)
        val after = File(diff.prevSqlPath).listFiles()

        log.error(
            """
            Before ${before.map { it.name }}
            After  ${after.map { it.name }}
            """.trimIndent(),
        )
    }
    /**
     * default-character-set=utf8mb4
     * default-collation=utf8mb4_0900_ai_ci
     * generator=skeema:1.12.3-community
     * host=0.0.0.0
     * password=temporary
     * schema=prototype_reservation
     * user=temporary
     *
     * [build/previous]
     * flavor=mysql:9.3
     *
     * [/Users/sanghyeonkim/Downloads/port/prototype-reservation-system/adapter-module/build/previous]
     * flavor=mysql:9.3
     */
}
