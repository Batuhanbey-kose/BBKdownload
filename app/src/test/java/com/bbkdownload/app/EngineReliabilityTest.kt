package com.bbkdownload.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class EngineReliabilityTest {
    @get:Rule val temporary = TemporaryFolder()

    private val bundledAsset get() = File("src/main/res/raw/ytdlp")

    @Test
    fun upgradesAndRepairsAnExistingExtractorWithoutRecopyingValidData() {
        val target = temporary.newFile("yt-dlp").apply { writeText("old extractor") }
        BundledEngine.install(target) { bundledAsset.inputStream() }
        assertTrue(bundledAsset.readBytes().contentEquals(target.readBytes()))

        BundledEngine.install(target) { error("A verified installation must not be rewritten") }
        target.writeText("interrupted extraction")
        BundledEngine.install(target) { bundledAsset.inputStream() }
        assertTrue(bundledAsset.readBytes().contentEquals(target.readBytes()))
    }

    @Test
    fun invalidReplacementPreservesPreviousExtractor() {
        val target = temporary.newFile("yt-dlp").apply { writeText("previous extractor") }
        assertThrows(IllegalStateException::class.java) {
            BundledEngine.install(target) { "corrupt download".byteInputStream() }
        }
        assertEquals("previous extractor", target.readText())
        assertEquals(listOf("yt-dlp"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun bundledVersionMatchesTheVersionShownInTheApp() {
        ZipFile(bundledAsset).use { zip ->
            val version = zip.getInputStream(zip.getEntry("yt_dlp/version.py")).bufferedReader().readText()
            assertTrue(version.contains("__version__ = '${BundledEngine.VERSION}'"))
        }
    }

    @Test
    fun retriesTransientFailuresAtMostThreeTimes() {
        val error = IOException("Unable to download: HTTP Error 503: Service Unavailable")
        assertTrue(EngineErrors.shouldRetry(error, 1))
        assertTrue(EngineErrors.shouldRetry(error, 2))
        assertFalse(EngineErrors.shouldRetry(error, 3))
        assertTrue(EngineErrors.shouldRetry(IOException("wrapper", IOException("Connection reset")), 1))
    }

    @Test
    fun doesNotRetryAuthenticationRateLimitsOrPermanentFailures() {
        listOf(
            "Please log in; previous request timed out",
            "HTTP Error 403: Forbidden", "HTTP Error 429: Too Many Requests",
            "This video is private", "No space left on device", "Unsupported URL",
            "Requested format is not available", "DRM protected content",
        ).forEach { assertFalse(it, EngineErrors.shouldRetry(IOException(it), 1)) }
    }

    @Test
    fun doesNotPublishUnmergedStreamsOrIntermediateFiles() {
        listOf(
            "clip [123].f137.mp4", "clip [123].temp.mp4", "clip.mp4.part",
            "clip.mp4.part-Frag2", "clip.mp4.ytdl", ".hidden.mp4", "metadata.json",
        ).forEach { assertFalse(it, MediaPublisher.isFinishedMedia(it, DownloadMode.VIDEO)) }
        assertTrue(MediaPublisher.isFinishedMedia("clip [123].mp4", DownloadMode.VIDEO))
        assertFalse(MediaPublisher.isFinishedMedia("clip.m4a", DownloadMode.AUDIO))
        assertTrue(MediaPublisher.isFinishedMedia("clip.mp3", DownloadMode.AUDIO))
        assertFalse(MediaPublisher.isFinishedMedia("cover.webp", DownloadMode.IMAGE))
        assertTrue(MediaPublisher.isFinishedMedia("cover.jpg", DownloadMode.IMAGE))
    }

    @Test
    fun allVideoFallbacksRespectTheSelectedQualityIncludingTikTok() {
        listOf(false, true).forEach { combined ->
            listOf(false, true).forEach { compatible ->
                val formats = DownloadCommandBuilder.videoFormat("480p", combined, compatible).split('/')
                assertTrue(formats.all { "height<=480" in it })
            }
        }
    }

    @Test
    fun errorMessagesHidePrivateLinksAndCredentials() {
        val sanitized = EngineErrors.sanitize(
            "https://user:secret@x.com/private/status/123?token=secret token=hidden sessionid=private Authorization: bearer-secret",
        )
        listOf("secret", "hidden", "private", "/status/123").forEach { assertFalse(sanitized.contains(it)) }
        assertTrue(sanitized.contains("https://x.com/…"))
    }

    @Test
    fun retryStateKeepsOnlyFailedUrlsAndOriginalSettings() {
        val failed = DownloadConfig(listOf("https://x.com/user/status/1"), DownloadMode.AUDIO, "720p", false)
        DownloadStateStore.resetForStart(3)
        DownloadStateStore.partial(2, "1 bağlantı tamamlanamadı", failed)
        assertEquals(failed, DownloadStateStore.state.value.retryConfig)
        assertEquals(2, DownloadStateStore.state.value.completedFiles)
        assertFalse(DownloadStateStore.state.value.running)
        DownloadStateStore.resetForStart(1)
        assertEquals(null, DownloadStateStore.state.value.retryConfig)
    }
}
