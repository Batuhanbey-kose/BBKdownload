package com.bbkdownload.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionValidationTest {
    @Test
    fun doesNotMixIncompleteXSessionsAcrossDomains() {
        val incomplete = listOf(
            ExportedCookie(".x.com", "auth_token", "test-auth"),
            ExportedCookie(".twitter.com", "ct0", "test-csrf"),
        )
        assertFalse(SessionCookieStore.hasRequiredCookies(SessionPlatform.X, incomplete))
        assertTrue(SessionCookieStore.hasRequiredCookies(
            SessionPlatform.X, incomplete + ExportedCookie(".x.com", "ct0", "test-csrf"),
        ))
    }

    @Test
    fun rejectsBlankAndUnrelatedCookiesButAcceptsInstagramLoginForThreads() {
        assertFalse(SessionCookieStore.hasRequiredCookies(
            SessionPlatform.INSTAGRAM, listOf(ExportedCookie(".instagram.com", "sessionid", "")),
        ))
        assertFalse(SessionCookieStore.hasRequiredCookies(
            SessionPlatform.INSTAGRAM, listOf(ExportedCookie(".unrelated.invalid", "sessionid", "test-session")),
        ))
        assertTrue(SessionCookieStore.hasRequiredCookies(
            SessionPlatform.THREADS, listOf(ExportedCookie(".instagram.com", "sessionid", "test-session")),
        ))
    }

    @Test
    fun loginNavigationRequiresHttpsTrustedOriginAndStandardPort() {
        val platform = SessionPlatform.INSTAGRAM
        assertTrue(platform.isAllowedLoginUrl("https://www.instagram.com/accounts/login/"))
        listOf(
            "http://www.instagram.com/", "https://user:pass@www.instagram.com/",
            "https://www.instagram.com:8443/", "https://www.instagram.com.unrelated.invalid/",
            "javascript:alert(1)", "file:///sdcard/test.html",
        ).forEach { assertFalse(it, platform.isAllowedLoginUrl(it)) }
    }

    @Test
    fun automaticSavingWaitsUntilLoginAndChallengesAreFinished() {
        val platform = SessionPlatform.X
        assertFalse(platform.isSessionPage("https://x.com/i/flow/login"))
        assertFalse(platform.isSessionPage("https://x.com/account/challenge"))
        assertFalse(platform.isSessionPage("https://accounts.google.com/"))
        assertTrue(platform.isSessionPage("https://x.com/home"))
    }
}
