package com.reservation.config.persistence.migration
import com.reservation.utilities.logger.loggerFactory
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object InitializeSkeema {
    val log = loggerFactory<InitializeSkeema>()

    fun install() {
        val installDirectory = "build/tools/skeema"
        val downloadDirectory = "build/skeema.tar.gz"
        val url = getUrl()

        downloadSkeema(url, installDirectory, downloadDirectory)
        installSkeema(installDirectory, downloadDirectory)
    }

    private fun getUrl(): String {
        val osName = System.getProperty("os.name").lowercase()
        val platform =
            when {
                osName.contains("mac") -> "darwin"
                osName.contains("linux") -> "linux"
                osName.contains("win") -> "windows"
                else -> error("Unsupported OS: $osName")
            }

        log.error("🟢 OS: $osName")

        val url = "https://github.com/skeema/skeema/releases/download"
        return "$url/v1.13.1/skeema_1.13.1_${platform}_amd64.tar.gz"
    }

    private fun downloadSkeema(
        path: String,
        installDirectory: String,
        downloadDirectory: String,
    ) {
        val archive = File(downloadDirectory)
        val install = File(installDirectory)
        val url = URI(path).toURL()

        if (!install.exists()) {
            log.error("📦 Downloading Skeema...")
            url.openStream().use { input ->
                Files.copy(input, archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun installSkeema(
        installDirectory: String,
        downloadDirectory: String,
    ) {
        val archive = File(downloadDirectory)
        val skeemaInstallDir = File(installDirectory)

        val process =
            ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", skeemaInstallDir.absolutePath)
                .inheritIO() // 콘솔 출력 연결 (원하면 생략 가능)
                .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            error("tar extraction failed with exit code $exitCode")
        }
    }
}
