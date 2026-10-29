package com.bbkdownload.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
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
    @Volatile private var timedOut = false
    private val mainHandler = Handler(Looper.getMainLooper())

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
                if (!startDownload(intent)) stopSelf(startId)
            }

            else -> if (!busy.get()) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelled.set(true)
        activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        timedOut = true
        cancelled.set(true)
        activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        DownloadStateStore.fail("Android arka plan süresi dolduğu için indirme durduruldu.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startDownload(intent: Intent): Boolean {
        if (busy.get()) {
            DownloadStateStore.appendLog("Bir indirme zaten devam ediyor; ikinci istek atlandı.")
            return true
        }
        val urls = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
            .flatMap(UrlTools::extract).distinct().take(100)
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
        timedOut = false
        DownloadStateStore.resetForStart(urls.size)
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(0, getString(R.string.notification_preparing), true),
            )
            executor.execute { runDownload(config) }
        } catch (_: Exception) {
            busy.set(false)
            DownloadStateStore.fail("İndirme servisi başlatılamadı. Uygulamayı açıp yeniden deneyin.", config)
            return false
        }
        return true
    }

    private fun runDownload(config: DownloadConfig) {
        val root = File(
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir,
            "jobs/${UUID.randomUUID()}",
        )
        var publishedFiles = 0
        val failedUrls = mutableListOf<String>()
        var lastFailure = ""
        var outcome: JobOutcome = JobOutcome.Cancelled
        try {
            if (!root.mkdirs() && !root.isDirectory) {
                throw IllegalStateException("Geçici indirme klasörü oluşturulamadı.")
            }
            initializeEngine()

            config.urls.forEachIndexed { index, url ->
                checkCancelled()
                val itemDirectory = File(root, "item-${index + 1}")
                try {
                    publishedFiles += downloadItem(config, index, url, itemDirectory)
                } catch (error: Exception) {
                    if (error is PartialPublishException) publishedFiles += error.publishedCount
                    rethrowCancellation(error)
                    failedUrls += url
                    lastFailure = EngineErrors.friendly(error, url)
                    DownloadStateStore.appendLog("${EngineErrors.safeHost(url)}: $lastFailure")
                    DownloadStateStore.appendLog("Sıradaki bağlantıyla devam ediliyor.")
                } finally {
                    itemDirectory.deleteRecursively()
                }
                DownloadStateStore.update(progress = (index + 1) * 100 / config.urls.size)
            }

            checkCancelled()
            val retryConfig = config.copy(urls = failedUrls.toList())
            val summary = "${failedUrls.size} bağlantı tamamlanamadı. $lastFailure"
            outcome = when {
                failedUrls.isEmpty() -> JobOutcome.Success(publishedFiles)
                publishedFiles > 0 -> JobOutcome.Partial(publishedFiles, summary, retryConfig)
                else -> JobOutcome.Failure(summary, retryConfig)
            }
        } catch (_: YoutubeDL.CanceledException) {
            outcome = JobOutcome.Cancelled
        } catch (_: MediaCopyCancelledException) {
            outcome = JobOutcome.Cancelled
        } catch (_: InterruptedException) {
            outcome = JobOutcome.Cancelled
            Thread.currentThread().interrupt()
        } catch (error: Exception) {
            outcome = JobOutcome.Failure(EngineErrors.friendly(error, ""), config)
        } finally {
            activeProcessId = null
            root.deleteRecursively()
            val finalOutcome = outcome
            val savedFiles = publishedFiles
            mainHandler.post {
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (!timedOut) applyOutcome(finalOutcome, savedFiles)
                busy.set(false)
                stopSelf()
            }
        }
    }

    private fun downloadItem(config: DownloadConfig, index: Int, url: String, directory: File): Int {
        check(directory.mkdirs() || directory.isDirectory) { "Geçici indirme klasörü oluşturulamadı." }
        val preferCombined = SessionPlatform.fromUrl(url) == SessionPlatform.TIKTOK
        // Thumbnail conversion also needs FFmpeg. Combined TikTok MP4s do not.
        if (config.mode != DownloadMode.VIDEO || !preferCombined) initializeFfmpeg()
        val platform = SessionPlatform.fromUrl(url)?.displayName ?: "YouTube"
        DownloadStateStore.appendLog("[${index + 1}/${config.urls.size}] ${EngineErrors.safeHost(url)}")
        DownloadStateStore.update(
            progress = index * 100 / config.urls.size,
            status = "$platform indiriliyor",
            detail = "Bağlantı ${index + 1}/${config.urls.size}",
        )
        var executeError: Exception? = null
        try {
            executeWithRetry(config, index, url, directory, platform, preferCombined)
        } catch (error: Exception) {
            rethrowCancellation(error)
            executeError = error
        }
        checkCancelled()
        val saved = MediaPublisher.publishAll(
            context = this,
            stagingDirectory = directory,
            mode = config.mode,
            allowEmpty = executeError != null,
            shouldCancel = cancelled::get,
        )
        if (executeError != null) {
            if (saved > 0) throw PartialPublishException(saved, executeError)
            throw executeError
        }
        return saved
    }

    private fun executeWithRetry(
        config: DownloadConfig,
        index: Int,
        url: String,
        directory: File,
        platform: String,
        preferCombined: Boolean,
    ) {
        var attempt = 1
        var compatibleVideo = false
        while (true) {
            checkCancelled()
            val request = DownloadCommandBuilder.apply(
                YoutubeDLRequest(url),
                DownloadCommandBuilder.options(
                    mode = config.mode,
                    quality = config.quality,
                    includeCollection = config.includeCollection,
                    outputTemplate = File(directory, "%(title).150B [%(id)s].%(ext)s").absolutePath,
                    cookieFile = SessionCookieStore.fileForUrl(this, url)?.absolutePath,
                    preferCombinedVideo = preferCombined,
                    compatibleVideo = compatibleVideo,
                ),
            )
            val processId = "medya-${UUID.randomUUID()}"
            activeProcessId = processId
            try {
                YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                    if (cancelled.get()) YoutubeDL.getInstance().destroyProcessById(processId)
                    val localProgress = if (progress.isFinite()) progress.coerceIn(0f, 100f) else 0f
                    val overall = (((index + localProgress / 100f) / config.urls.size) * 100f)
                        .roundToInt().coerceIn(0, 100)
                    val etaText = if (eta > 0) " • ${eta} sn kaldı" else ""
                    DownloadStateStore.update(
                        progress = maxOf(DownloadStateStore.state.value.progress, overall),
                        status = "$platform indiriliyor",
                        detail = "${index + 1}/${config.urls.size} • %${localProgress.roundToInt()}$etaText",
                    )
                    if (line.isNotBlank()) DownloadStateStore.appendLog(EngineErrors.sanitize(line))
                    updateProgressNotification(overall, "$platform indiriliyor")
                }
                return
            } catch (error: Exception) {
                rethrowCancellation(error)
                when {
                    config.mode == DownloadMode.VIDEO && !compatibleVideo && attempt < 3 &&
                        EngineErrors.classify(error) == EngineFailure.FORMAT -> {
                        compatibleVideo = true
                        initializeFfmpeg()
                        DownloadStateStore.appendLog("Kalite sınırı korunarak uyumlu biçim deneniyor.")
                    }
                    EngineErrors.shouldRetry(error, attempt) -> {
                        DownloadStateStore.update(status = "Bağlantı yeniden deneniyor", detail = "Deneme ${attempt + 1}/3")
                        DownloadStateStore.appendLog("Geçici ağ hatası. Deneme ${attempt + 1}/3...")
                        repeat(attempt * 8) {
                            checkCancelled()
                            Thread.sleep(250)
                        }
                    }
                    else -> throw error
                }
                attempt++
            } finally {
                activeProcessId = null
            }
        }
    }

    private fun rethrowCancellation(error: Exception) {
        checkCancelled()
        val cause = if (error is PartialPublishException) error.cause else error
        when (cause) {
            is YoutubeDL.CanceledException -> throw cause
            is MediaCopyCancelledException -> throw cause
            is InterruptedException -> throw cause
        }
    }

    private fun initializeEngine() {
        DownloadStateStore.update(status = "Motor hazırlanıyor", detail = "İlk açılış biraz sürebilir...")
        BundledEngine.prepare(applicationContext)
        checkCancelled()
        DownloadStateStore.appendLog("Doğrulanmış yt-dlp ${BundledEngine.VERSION} motoru hazır.")
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

    private fun applyOutcome(outcome: JobOutcome, savedFiles: Int) {
        when (outcome) {
            is JobOutcome.Success -> {
                DownloadStateStore.complete(outcome.files)
                showFinishedNotification(true, "${outcome.files} dosya kaydedildi")
            }

            is JobOutcome.Partial -> {
                DownloadStateStore.partial(outcome.files, outcome.message, outcome.retryConfig)
                showFinishedNotification(false, "${outcome.files} dosya kaydedildi; bazıları tamamlanamadı")
            }

            is JobOutcome.Failure -> {
                DownloadStateStore.fail(outcome.message, outcome.retryConfig)
                showFinishedNotification(false, getString(R.string.notification_open_for_details))
            }

            JobOutcome.Cancelled -> {
                DownloadStateStore.cancelled(savedFiles)
                showFinishedNotification(false, "İndirme iptal edildi")
            }
        }
    }

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
        data class Partial(val files: Int, val message: String, val retryConfig: DownloadConfig) : JobOutcome
        data class Failure(val message: String, val retryConfig: DownloadConfig) : JobOutcome
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
