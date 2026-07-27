package com.bbkdownload.app

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

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
        "https://www.threads.net/login",
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

    companion object {
        fun fromId(id: String?): SessionPlatform? = entries.firstOrNull { it.platformId == id }

        fun fromUrl(url: String): SessionPlatform? {
            val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
            return entries.firstOrNull { platform ->
                platform.urlDomains.any { domain -> host == domain || host.endsWith(".$domain") }
            }
        }
    }
}

private data class ExportedCookie(
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
        return file(context, platform).takeIf { it.isFile && it.length() > COOKIE_HEADER.length }
    }

    fun hasSession(context: Context, platform: SessionPlatform): Boolean {
        val file = file(context, platform)
        return file.isFile && file.length() > COOKIE_HEADER.length
    }

    fun delete(context: Context, platform: SessionPlatform): Boolean {
        val target = file(context, platform)
        val names = target.takeIf { it.isFile }
            ?.readLines()
            ?.filterNot { it.startsWith("#") }
            ?.mapNotNull { it.split('\t').getOrNull(5) }
            ?.toSet()
            .orEmpty()
        val manager = CookieManager.getInstance()
        platform.cookieSources.forEach { source ->
            names.forEach { name ->
                val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure; SameSite=None"
                manager.setCookie(
                    source.url,
                    expired,
                )
                manager.setCookie(
                    source.url,
                    "$expired; Domain=${source.exportDomain}",
                )
            }
            runCatching { URI(source.url) }.getOrNull()?.let { uri ->
                WebStorage.getInstance().deleteOrigin("${uri.scheme}://${uri.host}")
            }
        }
        manager.flush()
        return target.delete()
    }

    fun exportFromWebView(context: Context, platform: SessionPlatform): Int {
        val manager = CookieManager.getInstance()
        manager.flush()
        val cookies = linkedMapOf<Pair<String, String>, ExportedCookie>()
        platform.cookieSources.forEach { source ->
            manager.getCookie(source.url)
                ?.split(';')
                ?.map { it.trim() }
                ?.filter { it.contains('=') }
                ?.forEach { entry ->
                    val name = entry.substringBefore('=').trim()
                    val value = entry.substringAfter('=', "").replace("\t", "").replace("\n", "")
                    if (name.isNotBlank() && value.isNotBlank()) {
                        cookies[source.exportDomain to name] = ExportedCookie(
                            source.exportDomain,
                            name,
                            value,
                        )
                    }
                }
        }

        val names = cookies.values.map { it.name }.toSet()
        if (platform.requiredCookieGroups.any { group -> names.none { it in group } }) {
            throw IllegalStateException("Giriş çerezleri eksik. Önce platformda giriş yapın.")
        }

        val expires = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.DAYS.toSeconds(180)
        val body = buildString {
            append(COOKIE_HEADER)
            cookies.values.forEach { cookie ->
                append(cookie.domain)
                append("\tTRUE\t/\tTRUE\t")
                append(expires)
                append('\t')
                append(cookie.name)
                append('\t')
                append(cookie.value)
                append('\n')
            }
        }

        val target = file(context, platform)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(body, Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return cookies.size
    }
}
