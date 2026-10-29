package com.bbkdownload.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import com.bbkdownload.app.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var platform: SessionPlatform
    private var saving = false
    private var pageFailed = false

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
        AppAppearance.installInsets(this, binding.root)
        applyTheme()

        binding.loginTitle.text = getString(R.string.login_title, platform.displayName)
        binding.closeButton.setOnClickListener { finish() }
        binding.saveSessionButton.setOnClickListener { saveSession() }
        binding.saveSessionButton.isEnabled = false
        binding.reloadButton.setOnClickListener {
            pageFailed = false
            binding.reloadButton.visibility = View.GONE
            binding.loginStatus.setText(R.string.login_waiting)
            binding.loginWebView.loadUrl(platform.loginUrl)
        }

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
                val target = url.orEmpty()
                if (target != "about:blank" && !platform.isAllowedLoginUrl(target)) {
                    view?.stopLoading()
                    binding.loginOrigin.text = getString(R.string.login_blocked_origin)
                    return
                }
                pageFailed = false
                binding.reloadButton.visibility = View.GONE
                val host = runCatching { URI(target).host.orEmpty() }.getOrDefault("")
                binding.loginOrigin.text = if (host.isBlank()) "" else "https://$host"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.pageProgress.visibility = View.GONE
                checkSession()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showPageError()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) showPageError()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url
                if (platform.isAllowedLoginUrl(uri.toString())) return false
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
        if (savedInstanceState == null || binding.loginWebView.restoreState(savedInstanceState) == null) {
            binding.loginWebView.loadUrl(platform.loginUrl)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(1000)
                    checkSession()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.loginWebView.canGoBack()) binding.loginWebView.goBack() else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::binding.isInitialized) binding.loginWebView.saveState(outState)
        super.onSaveInstanceState(outState)
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
        if (saving || isFinishing) return
        saving = true
        try {
            val count = SessionCookieStore.exportFromWebView(this, platform)
            Toast.makeText(
                this,
                "${platform.displayName} oturumu kaydedildi ($count çerez).",
                Toast.LENGTH_LONG,
            ).show()
            setResult(RESULT_OK)
            finish()
        } catch (_: Exception) {
            saving = false
            pageFailed = true
            binding.loginStatus.setText(R.string.login_save_error)
        }
    }

    private fun checkSession() {
        if (saving || isFinishing || pageFailed) return
        val ready = SessionCookieStore.isLoginReady(platform)
        binding.saveSessionButton.isEnabled = ready
        if (ready && platform.isSessionPage(binding.loginWebView.url.orEmpty())) {
            binding.loginStatus.setText(R.string.login_detected)
            saveSession()
        }
    }

    private fun showPageError() {
        pageFailed = true
        binding.pageProgress.visibility = View.GONE
        binding.loginStatus.setText(R.string.login_page_error)
        binding.reloadButton.visibility = View.VISIBLE
    }

    private fun applyTheme() = with(binding) {
        val theme = AppPreferences.theme(this@LoginActivity)
        AppAppearance.applyWindow(this@LoginActivity, theme)
        root.setBackgroundColor(theme.background)
        loginTitle.setTextColor(theme.primaryText)
        loginHelp.setTextColor(theme.secondaryText)
        loginHelp.setBackgroundColor(theme.panel)
        loginStatus.setTextColor(theme.secondaryText)
        loginOrigin.setTextColor(theme.accent)
        loginOrigin.setBackgroundColor(theme.panelAlt)
        closeButton.setTextColor(theme.secondaryText)
        reloadButton.setTextColor(theme.accent)
        saveSessionButton.backgroundTintList = ColorStateList.valueOf(theme.accent)
        saveSessionButton.setTextColor(theme.panel)
        pageProgress.setIndicatorColor(theme.accent)
        pageProgress.trackColor = theme.panelAlt
    }

    companion object {
        private const val EXTRA_PLATFORM_ID = "platform_id"

        fun intent(context: Context, platform: SessionPlatform): Intent =
            Intent(context, LoginActivity::class.java)
                .putExtra(EXTRA_PLATFORM_ID, platform.platformId)
    }
}
