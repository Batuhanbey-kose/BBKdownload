package com.bbkdownload.app

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.util.AtomicFile
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

data class CookieSource(
    val url: String,
    val exportDomain: String,
)

enum class SessionPlatform(
    val platformId: String,
    val displayName: String,
    val loginUrl: String,
    val cookieSources: List<CookieSource>,
    val requiredCookieGroups: List<Set<String>>,
    val urlDomains: Set<String>,
    val authHosts: Set<String>,
) {
    TIKTOK(
        "tiktok",
        "TikTok",
        "https://www.tiktok.com/login",
        listOf(
            CookieSource("https://www.tiktok.com/", ".tiktok.com"),
            CookieSource("https://www.tiktok.com/login", ".tiktok.com"),
        ),
        listOf(setOf("sessionid", "sessionid_ss", "sid_tt", "sid_guard")),
        setOf("tiktok.com", "tiktokv.com"),
        setOf("accounts.google.com", "appleid.apple.com", "www.facebook.com", "m.facebook.com"),
    ),
    INSTAGRAM(
        "instagram",
        "Instagram",
        "https://www.instagram.com/accounts/login/",
        listOf(
            CookieSource("https://www.instagram.com/", ".instagram.com"),
            CookieSource("https://www.instagram.com/accounts/login/", ".instagram.com"),
        ),
        listOf(setOf("sessionid")),
        setOf("instagram.com", "instagr.am"),
        setOf("www.facebook.com", "m.facebook.com"),
    ),
    X(
        "x",
        "X",
        "https://x.com/i/flow/login",
        listOf(
            CookieSource("https://twitter.com/", ".twitter.com"),
            CookieSource("https://x.com/", ".x.com"),
        ),
        listOf(setOf("auth_token"), setOf("ct0")),
        setOf("x.com", "twitter.com", "t.co"),
        setOf("accounts.google.com", "appleid.apple.com"),
    ),
    FACEBOOK(
        "facebook",
        "Facebook",
        "https://www.facebook.com/login/",
        listOf(
            CookieSource("https://m.facebook.com/", ".facebook.com"),
            CookieSource("https://www.facebook.com/", ".facebook.com"),
        ),
        listOf(setOf("c_user"), setOf("xs")),
        setOf("facebook.com", "fb.watch"),
        emptySet(),
    ),
    REDDIT(
        "reddit",
        "Reddit",
        "https://www.reddit.com/login/",
        listOf(
            CookieSource("https://old.reddit.com/", ".reddit.com"),
            CookieSource("https://www.reddit.com/", ".reddit.com"),
        ),
        listOf(setOf("reddit_session")),
        setOf("reddit.com", "redd.it"),
        setOf("accounts.google.com", "appleid.apple.com"),
    ),
    THREADS(
        "threads",
        "Threads",
        "https://www.threads.com/login",
        listOf(
            CookieSource("https://www.instagram.com/", ".instagram.com"),
            CookieSource("https://www.threads.net/", ".threads.net"),
            CookieSource("https://www.threads.com/", ".threads.com"),
        ),
        listOf(setOf("sessionid")),
        setOf("threads.net", "threads.com"),
        setOf("www.instagram.com", "accountscenter.instagram.com", "www.facebook.com", "m.facebook.com"),
    );

    fun isAllowedLoginHost(host: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        val platformDomains = urlDomains + cookieSources.mapNotNull {
            runCatching { URI(it.url).host }.getOrNull()
        }
        return platformDomains.any { domain ->
            normalized == domain || normalized.endsWith(".$domain")
        } || normalized in authHosts
    }

    fun isAllowedLoginUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
            uri.port in setOf(-1, 443) && isAllowedLoginHost(uri.host.orEmpty())
    }.getOrDefault(false)

    fun isSessionPage(url: String): Boolean {
        if (!isAllowedLoginUrl(url)) return false
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase()
        val ownDomain = cookieSources.any { host == it.exportDomain.trimStart('.') || host.endsWith(it.exportDomain) }
        val path = uri.path.orEmpty().lowercase()
        return ownDomain && listOf("login", "signin", "sign_in", "challenge", "checkpoint").none { it in path }
    }

    companion object {
        fun fromId(id: String?): SessionPlatform? = entries.firstOrNull { it.platformId == id }

        fun fromUrl(url: String): SessionPlatform? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null || uri.port !in setOf(-1, 443)) return null
            val host = uri.host.orEmpty().lowercase().trimEnd('.')
            return entries.firstOrNull { platform ->
                platform.urlDomains.any { domain -> host == domain || host.endsWith(".$domain") }
            }
        }
    }
}

internal data class ExportedCookie(
    val domain: String,
    val name: String,
    val value: String,
)

object SessionCookieStore {
    private const val COOKIE_HEADER = "# Netscape HTTP Cookie File\n"

    fun file(context: Context, platform: SessionPlatform): File {
        val directory = File(context.filesDir, "sessions").apply { mkdirs() }
        return File(directory, "${platform.platformId}.txt")
    }

    fun fileForUrl(context: Context, url: String): File? {
        val platform = SessionPlatform.fromUrl(url) ?: return null
        return file(context, platform).takeIf { hasSession(context, platform) }
    }

    fun hasSession(context: Context, platform: SessionPlatform): Boolean {
        return runCatching {
            val target = file(context, platform)
            val now = System.currentTimeMillis() / 1000
            val lines = AtomicFile(target).openRead().bufferedReader().use { it.readLines() }
            val cookies = lines.mapNotNull { line ->
                val fields = line.removePrefix("#HttpOnly_").split('\t')
                if (fields.size != 7 || fields[0].startsWith('#')) return@mapNotNull null
                val expires = fields[4].toLongOrNull() ?: return@mapNotNull null
                if (expires != 0L && expires <= now) return@mapNotNull null
                ExportedCookie(fields[0], fields[5], fields[6])
            }
            hasRequiredCookies(platform, cookies)
        }.getOrDefault(false)
    }

    fun delete(context: Context, platform: SessionPlatform, onComplete: (Boolean) -> Unit) {
        val target = file(context, platform)
        val savedNames = runCatching { target.takeIf { it.isFile }?.readLines() }.getOrNull()
            ?.map { it.removePrefix("#HttpOnly_") }
            ?.filterNot { it.startsWith("#") }
            ?.mapNotNull { it.split('\t').getOrNull(5) }
            ?.toSet()
            .orEmpty()
        val manager = CookieManager.getInstance()
        val names = savedNames + readCookies(platform).map { it.name }
        AtomicFile(target).delete()
        val removed = !target.exists()
        platform.cookieSources.forEach { source ->
            runCatching { URI(source.url) }.getOrNull()?.let { uri ->
                WebStorage.getInstance().deleteOrigin("${uri.scheme}://${uri.host}")
            }
        }
        val pending = AtomicInteger(platform.cookieSources.size * names.size * 2)
        if (pending.get() == 0) {
            onComplete(removed)
            return
        }
        val cookieRemoved: (Boolean) -> Unit = {
            if (pending.decrementAndGet() == 0) {
                manager.flush()
                onComplete(removed)
            }
        }
        platform.cookieSources.forEach { source ->
            names.forEach { name ->
                val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure; SameSite=None"
                manager.setCookie(
                    source.url,
                    expired,
                    cookieRemoved,
                )
                manager.setCookie(
                    source.url,
                    "$expired; Domain=${source.exportDomain}",
                    cookieRemoved,
                )
            }
        }
    }

    fun isLoginReady(platform: SessionPlatform): Boolean = hasRequiredCookies(platform, readCookies(platform))

    internal fun hasRequiredCookies(platform: SessionPlatform, cookies: List<ExportedCookie>): Boolean =
        cookies.filter { cookie ->
            cookie.value.isNotBlank() && platform.cookieSources.any { it.exportDomain == cookie.domain }
        }.groupBy { it.domain }.values.any { domainCookies ->
            val names = domainCookies.map { it.name }.toSet()
            platform.requiredCookieGroups.all { group -> group.any { it in names } }
        }

    private fun readCookies(platform: SessionPlatform): List<ExportedCookie> {
        val manager = CookieManager.getInstance()
        val cookies = linkedMapOf<Pair<String, String>, ExportedCookie>()
        platform.cookieSources.forEach { source ->
            manager.getCookie(source.url)
                ?.split(';')
                ?.map { it.trim() }
                ?.filter { it.contains('=') }
                ?.forEach { entry ->
                    val name = entry.substringBefore('=').trim()
                    val value = entry.substringAfter('=', "").filterNot { it.isISOControl() }
                    if (Regex("[A-Za-z0-9_!#$%&'*+.^`|~-]+").matches(name) && value.isNotBlank()) {
                        cookies[source.exportDomain to name] = ExportedCookie(
                            source.exportDomain,
                            name,
                            value,
                        )
                    }
                }
        }
        return cookies.values.toList()
    }

    fun exportFromWebView(context: Context, platform: SessionPlatform): Int {
        CookieManager.getInstance().flush()
        val cookies = readCookies(platform)
        if (!hasRequiredCookies(platform, cookies)) {
            throw IllegalStateException("Giriş çerezleri eksik. Önce platformda giriş yapın.")
        }

        val body = buildString {
            append(COOKIE_HEADER)
            cookies.forEach { cookie ->
                append(cookie.domain)
                append("\tTRUE\t/\tTRUE\t")
                // CookieManager does not expose expiry. Preserve session semantics
                // rather than inventing a six-month validity period.
                append(0)
                append('\t')
                append(cookie.name)
                append('\t')
                append(cookie.value)
                append('\n')
            }
        }

        val target = file(context, platform)
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            output.write(body.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        return cookies.size
    }
}
