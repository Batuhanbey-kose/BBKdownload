package com.bbkdownload.app

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

class MediaCopyCancelledException : Exception()

class PartialPublishException(
    val publishedCount: Int,
    cause: Throwable,
) : Exception(cause)

object MediaPublisher {
    private val outputDirectory = "${Environment.DIRECTORY_DOWNLOADS}/BBKdownload"

    fun publishAll(
        context: Context,
        stagingDirectory: File,
        mode: DownloadMode,
        allowEmpty: Boolean = false,
        shouldCancel: () -> Boolean = { false },
    ): Int {
        val files = stagingDirectory.walkTopDown()
            .filter { file ->
                file.isFile && file.length() > 0 && isFinishedMedia(file.name, mode)
            }
            .toList()
        if (files.isEmpty()) {
            if (allowEmpty) return 0
            throw IllegalStateException("İndirilen dosya bulunamadı.")
        }

        var published = 0
        files.forEach { file ->
            try {
                if (shouldCancel()) throw MediaCopyCancelledException()
                publish(context, file, shouldCancel)
                published++
            } catch (error: Throwable) {
                if (published > 0) throw PartialPublishException(published, error)
                throw error
            }
        }
        return published
    }

    internal fun isFinishedMedia(name: String, mode: DownloadMode): Boolean {
        val lower = name.lowercase()
        if (lower.startsWith(".") || Regex("""\.(?:f\d+|temp|part|frag\d+)(?:\.|$)""").containsMatchIn(lower)) return false
        val extension = lower.substringAfterLast('.', "")
        return when (mode) {
            DownloadMode.VIDEO -> extension in setOf("mp4", "mkv", "webm", "mov", "m4v")
            DownloadMode.AUDIO -> extension == "mp3"
            DownloadMode.IMAGE -> extension in setOf("jpg", "jpeg")
        }
    }

    private fun publish(context: Context, source: File, shouldCancel: () -> Boolean) {
        val resolver = context.contentResolver
        val extension = source.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "mp4", "m4v" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                "webm" -> "video/webm"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android İndirilenler klasöründe dosya oluşturamadı.")
        try {
            resolver.openOutputStream(target, "w")?.buffered()?.use { output ->
                source.inputStream().buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (shouldCancel()) throw MediaCopyCancelledException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IllegalStateException("İndirilen dosya yazılamadı.")
            if (shouldCancel()) throw MediaCopyCancelledException()
            resolver.update(
                target,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(target, null, null)
            throw error
        }
    }
}
