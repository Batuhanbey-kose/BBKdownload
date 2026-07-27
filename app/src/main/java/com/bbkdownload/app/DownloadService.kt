package com.bbkdownload.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class DownloadService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    @Volatile private var activeProcessId: String? = null
    @Volatile private var lastNotificationUpdate = 0L
    private var ffmpegInitialized = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                if (busy.get()) cancelDownload() else stopSelf(startId)
            }

            ACTION_START -> {
                if (!startDownload(intent, startId)) stopSelf(startId)
            }

            else -> if (!busy.get()) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelled.set(true)
        activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        DownloadStateStore.fail("Android arka plan süresi dolduğu için indirme durduruldu.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun startDownload(intent: Intent, startId: Int): Boolean {
        val urls = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
        if (urls.isEmpty()) return false
        if (!busy.compareAndSet(false, true)) {
            DownloadStateStore.appendLog("Bir indirme zaten devam ediyor; ikinci istek atlandı.")
            return true
        }
        val mode = runCatching {
            DownloadMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
        }.getOrDefault(DownloadMode.VIDEO)
        val config = DownloadConfig(
            urls = urls,
            mode = mode,
            quality = intent.getStringExtra(EXTRA_QUALITY) ?: "En iyi",
            includeCollection = intent.getBooleanExtra(EXTRA_COLLECTION, false),
        )
        cancelled.set(false)
        DownloadStateStore.resetForStart(urls.size)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(0, getString(R.string.notification_preparing), true),
        )
        executor.execute { runDownload(config, startId) }
        return true
    }

    private fun runDownload(config: DownloadConfig, startId: Int) {
        val root = File(
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "jobs/${System.currentTimeMillis()}",
        )
        var publishedFiles = 0
        var activeUrl = ""
        var outcome: JobOutcome = JobOutcome.Cancelled
        try {
            if (!root.mkdirs() && !root.isDirectory) {
                throw IllegalStateException("Geçici indirme klasörü oluşturulamadı.")
            }
            initializeEngine()

            config.urls.forEachIndexed { index, url ->
                activeUrl = url
                checkCancelled()
                val sessionPlatform = SessionPlatform.fromUrl(url)
                val preferCombinedVideo = sessionPlatform == SessionPlatform.TIKTOK
                if (config.mode == DownloadMode.AUDIO ||
                    (config.mode == DownloadMode.VIDEO && !preferCombinedVideo)
                ) {
                    initializeFfmpeg()
                }
                val itemDirectory = File(root, "item-${index + 1}").apply { mkdirs() }
                val output = File(itemDirectory, "%(title).150B [%(id)s].%(ext)s").absolutePath
                val cookieFile = SessionCookieStore.fileForUrl(this, url)?.absolutePath
                val request = YoutubeDLRequest(url)
                DownloadCommandBuilder.apply(
                    request,
                    DownloadCommandBuilder.options(
                        mode = config.mode,
                        quality = config.quality,
                        includeCollection = config.includeCollection,
                        outputTemplate = output,
                        cookieFile = cookieFile,
                        preferCombinedVideo = preferCombinedVideo,
                    ),
                )

                val platform = sessionPlatform?.displayName ?: "Bağlantı"
                DownloadStateStore.appendLog("[${index + 1}/${config.urls.size}] ${safeHost(url)}")
                DownloadStateStore.update(
                    progress = index * 100 / config.urls.size,
                    status = "$platform indiriliyor",
                    detail = "Bağlantı ${index + 1}/${config.urls.size}",
                )

                val processId = "medya-${UUID.randomUUID()}"
                var executeError: Throwable? = null
                activeProcessId = processId
                try {
                    YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                        if (cancelled.get()) YoutubeDL.getInstance().destroyProcessById(processId)
                        val localProgress = progress.coerceIn(0f, 100f)
                        val overall = (((index + localProgress / 100f) / config.urls.size) * 100f)
                            .roundToInt()
                            .coerceIn(0, 100)
                        val etaText = if (eta > 0) " • ${eta} sn kaldı" else ""
                        DownloadStateStore.update(
                            progress = overall,
                            status = "$platform indiriliyor",
                            detail = "%${localProgress.roundToInt()}$etaText",
                        )
                        if (line.isNotBlank()) DownloadStateStore.appendLog(sanitizeEngineLine(line))
                        updateProgressNotification(overall, "$platform indiriliyor")
                    }
                } catch (error: Throwable) {
                    executeError = error
                } finally {
                    activeProcessId = null
                }

                checkCancelled()
                try {
                    publishedFiles += MediaPublisher.publishAll(
                        context = this,
                        stagingDirectory = itemDirectory,
                        allowEmpty = executeError != null,
                        shouldCancel = cancelled::get,
                    )
                } catch (error: PartialPublishException) {
                    publishedFiles += error.publishedCount
                    throw error.cause ?: error
                }
                itemDirectory.deleteRecursively()
                checkCancelled()
                if (executeError != null) throw executeError
            }

            checkCancelled()
            outcome = JobOutcome.Success(publishedFiles)
        } catch (_: YoutubeDL.CanceledException) {
            outcome = JobOutcome.Cancelled
        } catch (_: MediaCopyCancelledException) {
            outcome = JobOutcome.Cancelled
        } catch (_: InterruptedException) {
            outcome = JobOutcome.Cancelled
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            val message = friendlyError(error, activeUrl)
            outcome = if (publishedFiles > 0) {
                JobOutcome.Partial(publishedFiles, message)
            } else {
                JobOutcome.Failure(message)
            }
        } finally {
            activeProcessId = null
            root.deleteRecursively()
            stopForeground(STOP_FOREGROUND_REMOVE)
            applyOutcome(outcome)
            busy.set(false)
            stopSelfResult(startId)
        }
    }

    private fun initializeEngine() {
        DownloadStateStore.update(status = "Motor hazırlanıyor", detail = "İlk açılış biraz sürebilir...")
        YoutubeDL.getInstance().init(applicationContext)
        DownloadStateStore.appendLog("APK içindeki sabit yt-dlp motoru hazır.")
    }

    private fun initializeFfmpeg() {
        if (ffmpegInitialized) return
        DownloadStateStore.update(status = "FFmpeg hazırlanıyor", detail = "Ses ve video araçları açılıyor...")
        FFmpeg.getInstance().init(applicationContext)
        ffmpegInitialized = true
        DownloadStateStore.appendLog("FFmpeg motoru hazır.")
    }

    private fun checkCancelled() {
        if (cancelled.get()) throw YoutubeDL.CanceledException()
    }

    private fun cancelDownload() {
        cancelled.set(true)
        DownloadStateStore.update(status = "İptal ediliyor", detail = "Etkin dosya güvenle durduruluyor...")
        activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
    }

    private fun applyOutcome(outcome: JobOutcome) {
        when (outcome) {
            is JobOutcome.Success -> {
                DownloadStateStore.complete(outcome.files)
                showFinishedNotification(true, "${outcome.files} dosya kaydedildi")
            }

            is JobOutcome.Partial -> {
                DownloadStateStore.partial(outcome.files, outcome.message)
                showFinishedNotification(false, "${outcome.files} dosya kaydedildi; bazıları tamamlanamadı")
            }

            is JobOutcome.Failure -> {
                DownloadStateStore.fail(outcome.message)
                showFinishedNotification(false, getString(R.string.notification_open_for_details))
            }

            JobOutcome.Cancelled -> {
                DownloadStateStore.cancelled()
                showFinishedNotification(false, "İndirme iptal edildi")
            }
        }
    }

    private fun friendlyError(error: Throwable, url: String): String {
        val raw = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val lower = raw.lowercase()
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            ("log in" in lower || "login" in lower || "sign in" in lower) &&
                (host.endsWith("youtube.com") || host == "youtu.be") ->
                "YouTube ek doğrulama istedi. Bir süre sonra veya farklı ağda yeniden deneyin."
            "log in" in lower || "login" in lower || "sign in" in lower ->
                "Bu içerik oturum istiyor. Bağlantıyı ekleyip 'Oturum Aç' ile platforma giriş yapın."
            "private" in lower -> "İçerik gizli veya hesabınızın erişim izni yok."
            "requested format" in lower -> "Seçilen kalite bulunamadı. 'En iyi' kalitesini deneyin."
            "unsupported url" in lower -> "Bu bağlantı şu anda desteklenmiyor."
            "429" in lower -> "Platform geçici istek sınırı uyguladı. Bir süre sonra yeniden deneyin."
            "403" in lower || "forbidden" in lower -> "Platform erişimi reddetti. Oturum açıp yeniden deneyin."
            raw.isBlank() -> "İndirme motoru işlemi tamamlayamadı."
            else -> sanitizeEngineLine(raw.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty())
                .ifBlank { "İndirme motoru işlemi tamamlayamadı." }
        }
    }

    private fun safeHost(url: String): String = runCatching {
        URI(url).host.orEmpty().lowercase().ifBlank { "desteklenen platform" }
    }.getOrDefault("desteklenen platform")

    private fun sanitizeEngineLine(line: String): String = line
        .replace(Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)) { match ->
            "https://${safeHost(match.value)}/…"
        }
        .replace(
            Regex("""(?i)\b(token|auth|cookie|session|password|signature|sig)=\S+"""),
            "${'$'}1=<gizli>",
        )
        .takeLast(220)

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.notification_channel_description) },
        )
    }

    private fun buildNotification(progress: Int, text: String, ongoing: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(mainPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setProgress(100, progress, progress <= 0)
            .apply {
                if (ongoing) addAction(0, getString(R.string.cancel), cancelPendingIntent())
            }
            .build()

    @SuppressLint("MissingPermission")
    private fun updateProgressNotification(progress: Int, text: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate < 750) return
        lastNotificationUpdate = now
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(progress, text, true))
        }
    }

    @SuppressLint("MissingPermission")
    private fun showFinishedNotification(success: Boolean, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(
                getString(if (success) R.string.notification_complete else R.string.notification_failed),
            )
            .setContentText(text)
            .setContentIntent(mainPendingIntent())
            .setAutoCancel(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(FINISHED_NOTIFICATION_ID, notification)
        }
    }

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private sealed interface JobOutcome {
        data class Success(val files: Int) : JobOutcome
        data class Partial(val files: Int, val message: String) : JobOutcome
        data class Failure(val message: String) : JobOutcome
        data object Cancelled : JobOutcome
    }

    companion object {
        const val ACTION_START = "com.bbkdownload.app.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.bbkdownload.app.CANCEL_DOWNLOAD"
        const val EXTRA_URLS = "urls"
        const val EXTRA_MODE = "mode"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_COLLECTION = "collection"

        private const val CHANNEL_ID = "media_downloads"
        private const val NOTIFICATION_ID = 2101
        private const val FINISHED_NOTIFICATION_ID = 2102
    }
}
