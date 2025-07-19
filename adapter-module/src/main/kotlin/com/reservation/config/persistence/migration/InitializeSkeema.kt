package com.reservation.config.persistence.migration
import com.reservation.utilities.logger.loggerFactory
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object InitializeSkeema {
    private val log = loggerFactory<InitializeSkeema>()
    private const val VERSION = "1.12.3"

    data class SkeemaDirectory(
        val installDirectory: String,
        val downloadDirectory: String,
    )

    fun install(directory: SkeemaDirectory) {
        val installDirectory = directory.installDirectory
        val downloadDirectory = directory.downloadDirectory
        val url = getUrl()

        downloadSkeema(url, installDirectory, downloadDirectory)
        installSkeema(installDirectory, downloadDirectory)
    }

    private fun getUrl(): String {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val platform =
            when {
                osName.contains("mac") -> "mac"
                osName.contains("linux") -> "linux"
                osName.contains("win") -> ""
                else -> error("Unsupported OS: $osName")
            }
        val archName =
            when {
                arch.contains("x86_64") || arch.contains("amd64") -> "amd64"
                arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
                else -> error("Unsupported architecture: $arch")
            }

        log.error("🟢 OS: ${osName}_$archName")

        // https://github.com/skeema/skeema/releases/download/v1.12.3/skeema_1.12.3_linux_amd64.tar.gz
        val url = "https://github.com/skeema/skeema/releases/download"
        return "$url/v$VERSION/skeema_${VERSION}_${platform}_$archName.tar.gz"
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

        if (!archive.exists()) {
            archive.mkdirs()
        }
        if (!skeemaInstallDir.exists()) {
            skeemaInstallDir.mkdirs()
        }

        val process =
            ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", skeemaInstallDir.absolutePath)
                .inheritIO() // 콘솔 출력 연결 (원하면 생략 가능)
                .start()

        val exitCode = process.waitFor()
        log.error("🔴 result {}", exitCode)
        if (exitCode != 0) {
            error("tar extraction failed with exit code $exitCode")
        }
    }
}
