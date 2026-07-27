package com.bbkdownload.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadModelsTest {
    @Test
    fun extractsLinksFromShareTextAndKeepsOrder() {
        val xUrl = secureUrl("x.com", "/user/status/123/video/1")
        val tiktokUrl = secureUrl("vt.tiktok.com", "/short-id/")
        val text = "Bak $xUrl ve $tiktokUrl."

        assertEquals(
            listOf(xUrl, tiktokUrl),
            UrlTools.extract(text),
        )
    }

    @Test
    fun videoQualityIsStrictlyCapped() {
        val format = DownloadCommandBuilder.videoFormat("720p")

        assertTrue("height<=720" in format)
        assertFalse("height<=?720" in format)
    }

    @Test
    fun audioModeAddsMp3AndCookieOptions() {
        val options = DownloadCommandBuilder.options(
            mode = DownloadMode.AUDIO,
            quality = "En iyi",
            includeCollection = false,
            outputTemplate = "/tmp/%(title)s.%(ext)s",
            cookieFile = "/private/cookies.txt",
        )

        assertTrue(options.any { it.name == "--audio-format" && it.value == "mp3" })
        assertTrue(options.any { it.name == "--cookies" && it.value == "/private/cookies.txt" })
        assertTrue(options.any { it.name == "--no-playlist" })
    }

    @Test
    fun imageModeDownloadsOnlyThumbnail() {
        val options = DownloadCommandBuilder.options(
            mode = DownloadMode.IMAGE,
            quality = "En iyi",
            includeCollection = false,
            outputTemplate = "/tmp/%(title)s.%(ext)s",
            cookieFile = null,
        )

        assertTrue(options.any { it.name == "--skip-download" })
        assertTrue(options.any { it.name == "--write-thumbnail" })
        assertTrue(options.any { it.name == "--convert-thumbnails" && it.value == "jpg" })
    }

    @Test
    fun recognizesShortTikTokLinksForSession() {
        assertEquals(
            SessionPlatform.TIKTOK,
            SessionPlatform.fromUrl(secureUrl("vt.tiktok.com", "/short-id/")),
        )
    }

    @Test
    fun xSessionRequiresAuthTokenAndCsrfCookie() {
        assertEquals(
            listOf(setOf("auth_token"), setOf("ct0")),
            SessionPlatform.X.requiredCookieGroups,
        )
        assertTrue(SessionPlatform.X.cookieSources.any { it.exportDomain == ".x.com" })
        assertTrue(SessionPlatform.X.cookieSources.any { it.exportDomain == ".twitter.com" })
    }

    @Test
    fun loginWebViewRejectsUnrelatedHosts() {
        assertTrue(SessionPlatform.TIKTOK.isAllowedLoginHost("accounts.google.com"))
        assertFalse(SessionPlatform.TIKTOK.isAllowedLoginHost("reddit.com"))
        assertFalse(SessionPlatform.TIKTOK.isAllowedLoginHost("tiktok-login.attacker.invalid"))
    }

    @Test
    fun rejectsHttpPrivateAndUnsupportedLinks() {
        val text = listOf(
            insecureUrl("x.com", "/user/status/1"),
            "https://user:password@x.com/user/status/2",
            secureUrl("127.0.0.1", "/private"),
            secureUrl("unsupported.invalid", "/video"),
            secureUrl("x.com:8443", "/user/status/3"),
        ).joinToString(" ")

        assertTrue(UrlTools.extract(text).isEmpty())
    }

    @Test
    fun includesAtLeastTenThemes() {
        assertTrue(AppThemes.all.size >= 10)
        assertEquals(AppThemes.all.size, AppThemes.all.map { it.id }.toSet().size)
        assertTrue(AppThemes.all.any { it.label == "Neon BBK" })
    }

    @Test
    fun publicContentDoesNotRequireSessionCookies() {
        val options = DownloadCommandBuilder.options(
            mode = DownloadMode.VIDEO,
            quality = "En iyi",
            includeCollection = false,
            outputTemplate = "/tmp/%(title)s.%(ext)s",
            cookieFile = null,
        )

        assertFalse(options.any { it.name == "--cookies" })
    }

    @Test
    fun tiktokVideoUsesCombinedMp4WithoutFfmpegMerge() {
        val format = DownloadCommandBuilder.videoFormat("En iyi", preferCombined = true)

        assertEquals("best[ext=mp4]/best", format)
        assertFalse("+" in format)
    }

    private fun secureUrl(host: String, path: String) = "https://$host$path"

    private fun insecureUrl(host: String, path: String) = "http://$host$path"
}
