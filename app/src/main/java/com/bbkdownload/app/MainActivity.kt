package com.bbkdownload.app

import android.Manifest
import android.content.res.ColorStateList
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bbkdownload.app.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val qualityOptions = listOf("En iyi", "2160p", "1440p", "1080p", "720p", "480p")
    private val themeOptions = AppThemes.all.map { it.label }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            toast("Bildirim izni kapalı. İlerleme ve iptal uygulama açıkken kullanılabilir.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupQualityMenu()
        setupThemeMenu()
        setupActions()
        observeDownloadState()
        consumeShareIntent(intent)
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateSessionStatus()
    }

    private fun setupQualityMenu() {
        binding.qualityInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, qualityOptions),
        )
        binding.qualityInput.setText(qualityOptions.first(), false)
    }

    private fun setupThemeMenu() {
        binding.themeInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, themeOptions),
        )
        val savedTheme = getPreferences(MODE_PRIVATE).getString(PREF_THEME_ID, AppThemes.default.id)
        val theme = AppThemes.byId(savedTheme)
        binding.themeInput.setText(theme.label, false)
        applyTheme(theme)
        binding.themeInput.setOnItemClickListener { _, _, _, _ ->
            val selected = AppThemes.byLabel(binding.themeInput.text.toString())
            getPreferences(MODE_PRIVATE).edit { putString(PREF_THEME_ID, selected.id) }
            applyTheme(selected)
        }
    }

    private fun setupActions() {
        binding.pasteButton.setOnClickListener { pasteClipboard() }
        binding.clearButton.setOnClickListener { binding.urlInput.text?.clear() }
        binding.startButton.setOnClickListener { startDownload() }
        binding.cancelButton.setOnClickListener {
            startService(Intent(this, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL))
        }
        binding.sessionButton.setOnClickListener { openSession() }
        binding.deleteSessionButton.setOnClickListener { deleteSession() }
        binding.urlInput.doAfterTextChanged { updateSessionStatus() }
        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val videoMode = checkedId == binding.modeVideoButton.id
            binding.qualityInputLayout.isEnabled = videoMode
        }
    }

    private fun observeDownloadState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadStateStore.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: DownloadUiState) = with(binding) {
        statusText.text = state.status
        statusDetail.text = state.detail
        progressBar.setProgressCompat(state.progress, true)
        logText.text = state.logLines.joinToString("\n")
        startButton.isEnabled = !state.running
        cancelButton.isEnabled = state.running
        urlInput.isEnabled = !state.running
        pasteButton.isEnabled = !state.running
        clearButton.isEnabled = !state.running
        modeGroup.isEnabled = !state.running
        playlistCheck.isEnabled = !state.running
        qualityInputLayout.isEnabled = !state.running && modeGroup.checkedButtonId == modeVideoButton.id
        sessionButton.isEnabled = !state.running
        deleteSessionButton.isEnabled = !state.running
    }

    private fun startDownload() {
        val urls = UrlTools.extract(binding.urlInput.text?.toString().orEmpty())
        if (urls.isEmpty()) {
            binding.urlInputLayout.error = "Geçerli bir http/https bağlantısı ekleyin."
            return
        }
        binding.urlInputLayout.error = null
        if (DownloadStateStore.state.value.running) {
            toast("Bir indirme zaten devam ediyor.")
            return
        }
        val mode = when (binding.modeGroup.checkedButtonId) {
            binding.modeAudioButton.id -> DownloadMode.AUDIO
            binding.modeImageButton.id -> DownloadMode.IMAGE
            else -> DownloadMode.VIDEO
        }
        val serviceIntent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putStringArrayListExtra(DownloadService.EXTRA_URLS, ArrayList(urls))
            putExtra(DownloadService.EXTRA_MODE, mode.name)
            putExtra(DownloadService.EXTRA_QUALITY, binding.qualityInput.text.toString())
            putExtra(DownloadService.EXTRA_COLLECTION, binding.playlistCheck.isChecked)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        binding.startButton.isEnabled = false
        binding.statusText.setText(R.string.status_service_starting)
        binding.statusDetail.setText(R.string.status_engine_preparing)
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) {
            toast("Panoda bağlantı bulunamadı.")
            return
        }
        val current = binding.urlInput.text?.toString().orEmpty().trim()
        binding.urlInput.setText(listOf(current, text.trim()).filter { it.isNotBlank() }.joinToString("\n"))
        binding.urlInput.setSelection(binding.urlInput.text?.length ?: 0)
    }

    private fun openSession() {
        val platform = currentPlatform()
        if (platform == null) {
            toast("Önce TikTok, Instagram, X, Facebook, Reddit veya Threads bağlantısı ekleyin.")
            return
        }
        startActivity(LoginActivity.intent(this, platform))
    }

    private fun deleteSession() {
        val platform = currentPlatform()
        if (platform == null || !SessionCookieStore.hasSession(this, platform)) {
            toast("Silinecek kayıtlı oturum yok.")
            return
        }
        SessionCookieStore.delete(this, platform)
        updateSessionStatus()
        toast("${platform.displayName} oturumu silindi.")
    }

    private fun updateSessionStatus() {
        if (!::binding.isInitialized) return
        val platform = currentPlatform()
        val hasSession = platform?.let { SessionCookieStore.hasSession(this, it) } == true
        binding.sessionStatus.text = when {
            platform == null -> getString(R.string.session_none)
            hasSession -> getString(R.string.session_saved, platform.displayName)
            else -> "${platform.displayName} için oturum gerekirse giriş yapın"
        }
        binding.deleteSessionButton.visibility = if (hasSession) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun currentPlatform(): SessionPlatform? {
        val url = UrlTools.extract(binding.urlInput.text?.toString().orEmpty()).firstOrNull() ?: return null
        return SessionPlatform.fromUrl(url)
    }

    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (shared.isNotBlank()) {
            binding.urlInput.setText(shared)
            binding.urlInput.setSelection(shared.length)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun applyTheme(theme: AppThemePalette) = with(binding) {
        rootContainer.setBackgroundColor(theme.background)
        listOf(linkPanel, settingsPanel, statusPanel).forEach {
            it.background = rounded(theme.panel, theme.border, 16)
        }
        sessionPanel.background = rounded(theme.panelAlt, theme.border, 12)
        watermarkNote.background = rounded(theme.accentDark, theme.accent, 12)

        listOf(appTitle, statusText, sessionTitle).forEach { it.setTextColor(theme.primaryText) }
        listOf(appSubtitle, statusDetail, sessionStatus, playlistCheck).forEach { it.setTextColor(theme.secondaryText) }
        listOf(platformText, logText, legalText).forEach { it.setTextColor(theme.faintText) }
        listOf(linksTitle, settingsTitle, watermarkNote).forEach { it.setTextColor(theme.accent) }

        styleInput(urlInputLayout, theme)
        styleInput(themeInputLayout, theme)
        styleInput(qualityInputLayout, theme)
        listOf(urlInput, themeInput, qualityInput).forEach {
            it.setTextColor(theme.primaryText)
            it.setHintTextColor(theme.faintText)
        }

        listOf(modeVideoButton, modeAudioButton, modeImageButton, pasteButton, cancelButton).forEach {
            styleButton(it, theme.primaryText, theme.border, null)
        }
        val toggleBackground = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(theme.accentDark, android.graphics.Color.TRANSPARENT),
        )
        listOf(modeVideoButton, modeAudioButton, modeImageButton).forEach {
            it.backgroundTintList = toggleBackground
        }
        styleButton(sessionButton, theme.accent, theme.accent, null)
        styleButton(clearButton, theme.secondaryText, theme.border, null)
        styleButton(deleteSessionButton, theme.error, theme.error, null)
        styleButton(startButton, theme.background, theme.accent, theme.accent)
        cancelButton.setTextColor(theme.error)
        cancelButton.strokeColor = ColorStateList.valueOf(theme.error)

        progressBar.setIndicatorColor(theme.accent)
        progressBar.trackColor = theme.panelAlt
        playlistCheck.buttonTintList = ColorStateList.valueOf(theme.accent)
    }

    private fun styleInput(input: TextInputLayout, theme: AppThemePalette) {
        input.boxBackgroundColor = theme.panelAlt
        input.setBoxStrokeColor(theme.border)
        input.defaultHintTextColor = ColorStateList.valueOf(theme.secondaryText)
    }

    private fun styleButton(
        button: MaterialButton,
        textColor: Int,
        strokeColor: Int,
        fillColor: Int?,
    ) {
        button.setTextColor(textColor)
        button.strokeColor = ColorStateList.valueOf(strokeColor)
        button.backgroundTintList = ColorStateList.valueOf(fillColor ?: android.graphics.Color.TRANSPARENT)
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(fill)
        setStroke((1 * resources.displayMetrics.density).toInt().coerceAtLeast(1), stroke)
    }

    companion object {
        private const val PREF_THEME_ID = "theme_id"
    }
}
