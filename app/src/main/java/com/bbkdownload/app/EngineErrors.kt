package com.bbkdownload.app

import java.net.URI

enum class EngineFailure { NETWORK, FORMAT, AUTH, RATE_LIMIT, STORAGE, PRIVATE, UNSUPPORTED, OTHER }

object EngineErrors {
    fun message(error: Throwable): String = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.takeIf(String::isNotBlank) }
        .take(8)
        .joinToString("\n")

    fun classify(error: Throwable): EngineFailure {
        val text = message(error).lowercase()
        return when {
            "no space left" in text || "enospc" in text -> EngineFailure.STORAGE
            "429" in text || "too many requests" in text -> EngineFailure.RATE_LIMIT
            listOf("log in", "login", "sign in", "cookies", "403", "forbidden").any { it in text } -> EngineFailure.AUTH
            "private" in text || "members-only" in text -> EngineFailure.PRIVATE
            "unsupported url" in text -> EngineFailure.UNSUPPORTED
            "requested format" in text || "no video formats found" in text -> EngineFailure.FORMAT
            listOf(
                "timed out", "timeout", "connection reset", "connection refused", "connection aborted",
                "temporary failure", "name resolution", "network is unreachable", "unable to resolve host",
                "remote end closed", "http error 500", "http error 502", "http error 503", "http error 504",
            ).any { it in text } -> EngineFailure.NETWORK
            else -> EngineFailure.OTHER
        }
    }

    fun shouldRetry(error: Throwable, attempt: Int): Boolean =
        attempt < 3 && classify(error) == EngineFailure.NETWORK

    fun friendly(error: Throwable, url: String): String = when (classify(error)) {
        EngineFailure.STORAGE -> "Telefonda yeterli boş alan yok. Alan açıp yeniden deneyin."
        EngineFailure.RATE_LIMIT -> "Platform geçici istek sınırı uyguladı. Bir süre sonra yeniden deneyin."
        EngineFailure.AUTH -> if (SessionPlatform.fromUrl(url) == null) {
            "YouTube ek doğrulama istedi. Bir süre sonra veya farklı ağda yeniden deneyin."
        } else {
            "Platform oturum veya erişim izni istiyor. Oturumunuzu yenileyip başarısızları tekrar deneyin."
        }
        EngineFailure.PRIVATE -> "İçerik gizli veya hesabınızın erişim izni yok."
        EngineFailure.UNSUPPORTED -> "Bu bağlantı şu anda desteklenmiyor."
        EngineFailure.FORMAT -> "Seçilen kalite bulunamadı. Daha düşük kaliteyi veya En iyi seçeneğini deneyin."
        EngineFailure.NETWORK -> "Bağlantı kesildi veya platform yanıt vermedi. İnternetinizi kontrol edip tekrar deneyin."
        EngineFailure.OTHER -> sanitize(message(error).lineSequence().lastOrNull { it.isNotBlank() }.orEmpty())
            .ifBlank { "İndirme motoru işlemi tamamlayamadı." }
    }

    fun safeHost(url: String): String = runCatching {
        URI(url).host.orEmpty().lowercase().ifBlank { "desteklenen platform" }
    }.getOrDefault("desteklenen platform")

    fun sanitize(line: String): String = line
        .replace(Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)) {
            "https://${safeHost(it.value)}/…"
        }
        .replace(
            Regex("""(?i)\b(token|auth|authorization|cookie|session|sessionid|password|signature|sig)\s*[:=]\s*\S+"""),
            "${'$'}1=<gizli>",
        )
        .takeLast(220)
}
