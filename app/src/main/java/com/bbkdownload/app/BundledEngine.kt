package com.bbkdownload.app

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object BundledEngine {
    const val VERSION = "2026.10.26"
    const val SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"

    @Synchronized
    fun prepare(context: Context) {
        val directory = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}")
        install(File(directory, YoutubeDL.ytdlpBin)) { context.resources.openRawResource(R.raw.ytdlp) }
        YoutubeDL.getInstance().init(context.applicationContext)
    }

    // The library only copies yt-dlp on first install. Verify it on every new job so
    // existing installations also receive the APK's extractor update and repair.
    internal fun install(target: File, source: () -> InputStream) {
        if (target.isFile && digest(target) == SHA256) return
        val parent = requireNotNull(target.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Motor klasörü oluşturulamadı." }
        val temporary = File.createTempFile("yt-dlp-", ".tmp", parent)
        try {
            source().use { input -> temporary.outputStream().use { input.copyTo(it) } }
            check(digest(temporary) == SHA256) { "Paket içindeki motor doğrulanamadı." }
            Files.move(
                temporary.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
