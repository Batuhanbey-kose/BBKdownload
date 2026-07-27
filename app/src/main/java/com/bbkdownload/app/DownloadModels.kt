package com.bbkdownload.app

import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URI

enum class DownloadMode {
    VIDEO,
    AUDIO,
    IMAGE,
}

data class DownloadConfig(
    val urls: List<String>,
    val mode: DownloadMode,
    val quality: String,
    val includeCollection: Boolean,
)

data class DownloadUiState(
    val running: Boolean = false,
    val progress: Int = 0,
    val status: String = "Bağlantıları bekliyor",
    val detail: String = "Dosyalar İndirilenler/BBKdownload klasörüne kaydedilir.",
    val logLines: List<String> = emptyList(),
    val completedFiles: Int = 0,
    val error: Boolean = false,
)

object DownloadStateStore {
    private val mutableState = MutableStateFlow(DownloadUiState())
    val state = mutableState.asStateFlow()

    fun resetForStart(total: Int) {
        mutableState.value = DownloadUiState(
            running = true,
            status = "$total bağlantı sıraya alındı",
            detail = "İndirme motoru hazırlanıyor...",
            logLines = listOf("Yeni işlem başladı: $total bağlantı"),
        )
    }

    fun update(
        progress: Int? = null,
        status: String? = null,
        detail: String? = null,
    ) {
        mutableState.update {
            it.copy(
                progress = progress ?: it.progress,
                status = status ?: it.status,
                detail = detail ?: it.detail,
            )
        }
    }

    fun appendLog(message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        mutableState.update { it.copy(logLines = (it.logLines + clean).takeLast(8)) }
    }

    fun complete(files: Int) {
        mutableState.update {
            it.copy(
                running = false,
                progress = 100,
                status = "İndirme tamamlandı",
                detail = "$files dosya İndirilenler/BBKdownload klasörüne kaydedildi.",
                completedFiles = files,
                error = false,
            )
        }
        appendLog("Tüm bağlantılar başarıyla tamamlandı.")
    }

    fun fail(message: String) {
        mutableState.update {
            it.copy(
                running = false,
                status = "İndirme tamamlanamadı",
                detail = message,
                error = true,
            )
        }
        appendLog(message)
    }

    fun partial(files: Int, message: String) {
        mutableState.update {
            it.copy(
                running = false,
                status = "Kısmen tamamlandı",
                detail = "$files dosya kaydedildi. $message",
                completedFiles = files,
                error = true,
            )
        }
        appendLog("$files dosya kaydedildi; bazı öğeler tamamlanamadı.")
    }

    fun cancelled() {
        mutableState.update {
            it.copy(
                running = false,
                status = "İşlem iptal edildi",
                detail = "Geçici dosyalar temizlendi.",
                error = false,
            )
        }
        appendLog("İndirme kullanıcı tarafından iptal edildi.")
    }
}

object UrlTools {
    private val pattern = Regex("""(?:https://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val trailing = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '\'', '"')
    private val youtubeDomains = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    fun extract(text: String): List<String> {
        val result = linkedSetOf<String>()
        pattern.findAll(text).forEach { match ->
            var url = match.value.trimEnd { it in trailing }
            if (url.startsWith("www.", ignoreCase = true)) url = "https://$url"
            val valid = runCatching {
                val uri = URI(url)
                val host = uri.host.orEmpty().lowercase().trimEnd('.')
                val supportedDomains = youtubeDomains + SessionPlatform.entries.flatMap { it.urlDomains }
                uri.scheme.equals("https", ignoreCase = true) &&
                    uri.rawUserInfo == null &&
                    (uri.port == -1 || uri.port == 443) &&
                    supportedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
            }.getOrDefault(false)
            if (valid) result += url
        }
        return result.toList()
    }
}

data class CommandOption(val name: String, val value: String? = null)

object DownloadCommandBuilder {
    fun options(
        mode: DownloadMode,
        quality: String,
        includeCollection: Boolean,
        outputTemplate: String,
        cookieFile: String?,
        preferCombinedVideo: Boolean = false,
    ): List<CommandOption> = buildList {
        add(CommandOption("--newline"))
        add(CommandOption("--no-mtime"))
        add(CommandOption("--no-overwrites"))
        add(CommandOption("--trim-filenames", "180"))
        add(CommandOption("--retries", "5"))
        add(CommandOption("--fragment-retries", "5"))
        add(CommandOption("--socket-timeout", "30"))
        add(CommandOption("-o", outputTemplate))

        if (includeCollection) {
            add(CommandOption("--yes-playlist"))
            add(CommandOption("--playlist-items", "1:100"))
        } else {
            add(CommandOption("--no-playlist"))
        }

        if (!cookieFile.isNullOrBlank()) add(CommandOption("--cookies", cookieFile))

        when (mode) {
            DownloadMode.VIDEO -> {
                add(CommandOption("-f", videoFormat(quality, preferCombinedVideo)))
                add(CommandOption("--merge-output-format", "mp4"))
            }

            DownloadMode.AUDIO -> {
                add(CommandOption("-f", "bestaudio/best"))
                add(CommandOption("--extract-audio"))
                add(CommandOption("--audio-format", "mp3"))
                add(CommandOption("--audio-quality", "192K"))
            }

            DownloadMode.IMAGE -> {
                add(CommandOption("--skip-download"))
                add(CommandOption("--write-thumbnail"))
                add(CommandOption("--convert-thumbnails", "jpg"))
            }
        }
    }

    fun apply(request: YoutubeDLRequest, options: List<CommandOption>): YoutubeDLRequest {
        options.forEach { option ->
            if (option.value == null) request.addOption(option.name)
            else request.addOption(option.name, option.value)
        }
        return request
    }

    internal fun videoFormat(quality: String, preferCombined: Boolean = false): String {
        val height = when (quality) {
            "2160p" -> 2160
            "1440p" -> 1440
            "1080p" -> 1080
            "720p" -> 720
            "480p" -> 480
            else -> null
        }
        if (preferCombined) {
            return if (height == null) {
                "best[ext=mp4]/best"
            } else {
                "best[height<=$height][ext=mp4]/best[height<=$height]/best[ext=mp4]/best"
            }
        }
        return if (height == null) {
            "bestvideo*[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/bestvideo*+bestaudio/best"
        } else {
            "bestvideo*[height<=$height][ext=mp4]+bestaudio[ext=m4a]/" +
                "best[height<=$height][ext=mp4]/" +
                "bestvideo*[height<=$height]+bestaudio/best[height<=$height]"
        }
    }
}
