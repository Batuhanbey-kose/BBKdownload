package com.bbkdownload.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.bbkdownload.app.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var platform: SessionPlatform

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platform = SessionPlatform.fromId(intent.getStringExtra(EXTRA_PLATFORM_ID)) ?: run {
            finish()
            return
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(binding.root)

        binding.loginTitle.text = getString(R.string.login_title, platform.displayName)
        binding.closeButton.setOnClickListener { finish() }
        binding.saveSessionButton.setOnClickListener { saveSession() }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.loginWebView, true)
        }
        binding.loginWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
        }
        binding.loginWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.pageProgress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        binding.loginWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.pageProgress.visibility = View.VISIBLE
                val uri = runCatching { url.orEmpty().toUri() }.getOrNull()
                val host = uri?.host.orEmpty()
                val safeBlankPage = uri?.toString() == "about:blank"
                if (!safeBlankPage && (uri == null || uri.scheme != "https" || !platform.isAllowedLoginHost(host))) {
                    view?.stopLoading()
                    binding.loginOrigin.text = getString(R.string.login_blocked_origin)
                    return
                }
                binding.loginOrigin.text = if (host.isBlank()) "" else "https://$host"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.pageProgress.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url
                val host = uri.host.orEmpty()
                if (uri.scheme == "https" && platform.isAllowedLoginHost(host)) return false
                view.stopLoading()
                if (!request.hasGesture() || uri.scheme !in setOf("http", "https")) return true
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    true
                }
            }
        }
        binding.loginWebView.loadUrl(platform.loginUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.loginWebView.canGoBack()) binding.loginWebView.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.loginWebView.stopLoading()
            binding.loginWebView.webChromeClient = null
            binding.loginWebView.webViewClient = WebViewClient()
            binding.loginWebView.destroy()
        }
        super.onDestroy()
    }

    private fun saveSession() {
        try {
            val count = SessionCookieStore.exportFromWebView(this, platform)
            Toast.makeText(
                this,
                "${platform.displayName} oturumu kaydedildi ($count çerez).",
                Toast.LENGTH_LONG,
            ).show()
            setResult(RESULT_OK)
            finish()
        } catch (error: Throwable) {
            Toast.makeText(this, error.message ?: "Oturum kaydedilemedi.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val EXTRA_PLATFORM_ID = "platform_id"

        fun intent(context: Context, platform: SessionPlatform): Intent =
            Intent(context, LoginActivity::class.java)
                .putExtra(EXTRA_PLATFORM_ID, platform.platformId)
    }
}
