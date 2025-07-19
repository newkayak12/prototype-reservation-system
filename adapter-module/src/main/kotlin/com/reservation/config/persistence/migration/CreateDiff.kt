package com.reservation.config.persistence.migration

object CreateDiff {
    private fun getDifference(
        prevSqlPath: String,
        host: String,
        user: String,
        password: String,
        schema: String,
    ) {
        val diffResult =
            ProcessBuilder(
                "skeema",
                "diff",
                "--host=$host",
                "--user=$user",
                "--password=$password",
                "--schema=$schema",
                "--dir=$prevSqlPath",
            )
                .start()
                .inputStream
                .bufferedReader()
                .readText()
    }
}
