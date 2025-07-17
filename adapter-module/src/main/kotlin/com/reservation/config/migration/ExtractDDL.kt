package com.reservation.config.migration

import com.reservation.utilities.logger.loggerFactory
import javax.sql.DataSource
import java.io.File
import java.sql.Connection
import java.sql.ResultSet

object ExtractDDL {
    private val log = loggerFactory<ExtractDDL>()

    fun delete(filePath: String) {
        File(filePath).apply {
            if (exists()) {
                delete()
            }
        }
    }

    fun extract(
        dataSource: DataSource,
        filePath: String,
    ) {
        dataSource.connection.use { connection ->

            log.error("🔴 EXTRACT SCHEMA FROM ${connection.catalog}")
            val stmt = connection.createStatement()
            val resultSets =
                stmt.executeQuery(
                    "SELECT table_name " +
                        "FROM information_schema.tables " +
                        "WHERE table_schema = '${connection.catalog}'",
                )

            val ddlBuilder = StringBuilder()
            readAndWrite(connection, resultSets, ddlBuilder)

            File(filePath).apply {
                parentFile.mkdirs()
                writeText(ddlBuilder.toString())
            }
        }
    }

    private fun readAndWrite(
        connection: Connection,
        resultSets: ResultSet,
        ddlBuilder: StringBuilder,
    ) {
        while (resultSets.next()) {
            val tableName = resultSets.getString(1)
            ddlBuilder.append(getCreateTableDdl(tableName, connection))
        }
    }

    private fun getCreateTableDdl(
        tableName: String,
        connection: Connection,
    ): String {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SHOW CREATE TABLE `$tableName`").use { rs ->
                return if (rs.next()) rs.getString(2) + ";\n\n" else ""
            }
        }
    }
}
