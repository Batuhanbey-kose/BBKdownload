package com.bbkdownload.app

import android.Manifest
import android.app.DownloadManager
import android.content.res.ColorStateList
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val qualityOptions = listOf("En iyi", "2160p", "1440p", "1080p", "720p", "480p")
    private val themeOptions = AppThemes.all.map { it.label }
    private var sessionBusy = false
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
        AppAppearance.installInsets(this, binding.root)

        setupQualityMenu()
        setupThemeMenu()
        restoreDownloadSettings()
        setupActions()
        observeDownloadState()
        if (savedInstanceState == null) consumeShareIntent(intent)
        binding.engineVersion.text = getString(
            R.string.engine_version, packageManager.getPackageInfo(packageName, 0).versionName, BundledEngine.VERSION,
        )
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
            themedAdapter(qualityOptions),
        )
        val saved = AppPreferences.store(this).getString(PREF_QUALITY, qualityOptions.first())
        binding.qualityInput.setText(saved.takeIf { it in qualityOptions } ?: qualityOptions.first(), false)
        binding.qualityInput.setOnItemClickListener { _, _, _, _ -> saveDownloadSettings() }
    }

    private fun setupThemeMenu() {
        binding.themeInput.setAdapter(
            themedAdapter(themeOptions),
        )
        val theme = AppPreferences.theme(this)
        binding.themeInput.setText(theme.label, false)
        applyTheme(theme)
        binding.themeInput.setOnItemClickListener { _, _, _, _ ->
            val selected = AppThemes.byLabel(binding.themeInput.text.toString())
            AppPreferences.saveTheme(this, selected)
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
        binding.retryButton.setOnClickListener {
            DownloadStateStore.state.value.retryConfig?.let(::launchDownload)
        }
        binding.downloadsButton.setOnClickListener {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (_: Exception) {
                toast("Dosyalar uygulamasından İndirilenler/BBKdownload klasörünü açın.")
            }
        }
        binding.logToggleButton.setOnClickListener {
            val show = binding.logText.visibility != View.VISIBLE
            binding.logText.visibility = if (show) View.VISIBLE else View.GONE
            binding.logToggleButton.setText(if (show) R.string.hide_logs else R.string.show_logs)
        }
        binding.urlInput.doAfterTextChanged {
            binding.urlInputLayout.error = null
            updateSessionStatus()
            updateLinkCount()
        }
        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val videoMode = checkedId == binding.modeVideoButton.id
            binding.qualityInputLayout.isEnabled = videoMode && !DownloadStateStore.state.value.running
            saveDownloadSettings()
        }
        binding.playlistCheck.setOnCheckedChangeListener { _, _ -> saveDownloadSettings() }
        updateLinkCount()
    }

    private fun restoreDownloadSettings() {
        val preferences = AppPreferences.store(this)
        val button = when (preferences.getString(PREF_MODE, DownloadMode.VIDEO.name)) {
            DownloadMode.AUDIO.name -> binding.modeAudioButton.id
            DownloadMode.IMAGE.name -> binding.modeImageButton.id
            else -> binding.modeVideoButton.id
        }
        binding.modeGroup.check(button)
        binding.playlistCheck.isChecked = preferences.getBoolean(PREF_COLLECTION, false)
    }

    private fun selectedMode(): DownloadMode = when (binding.modeGroup.checkedButtonId) {
        binding.modeAudioButton.id -> DownloadMode.AUDIO
        binding.modeImageButton.id -> DownloadMode.IMAGE
        else -> DownloadMode.VIDEO
    }

    private fun saveDownloadSettings() {
        AppPreferences.store(this).edit {
            putString(PREF_QUALITY, binding.qualityInput.text.toString())
            putString(PREF_MODE, selectedMode().name)
            putBoolean(PREF_COLLECTION, binding.playlistCheck.isChecked)
        }
    }

    private fun updateLinkCount() {
        val count = UrlTools.extract(binding.urlInput.text?.toString().orEmpty()).size
        binding.linkCount.text = if (count == 0) "Desteklenen bir HTTPS bağlantısı ekleyin." else "$count benzersiz bağlantı • En fazla 100 bağlantı"
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
        startButton.isEnabled = !state.running && !sessionBusy
        cancelButton.isEnabled = state.running
        urlInput.isEnabled = !state.running
        pasteButton.isEnabled = !state.running
        clearButton.isEnabled = !state.running
        modeGroup.isEnabled = !state.running
        listOf(modeVideoButton, modeAudioButton, modeImageButton).forEach { it.isEnabled = !state.running }
        playlistCheck.isEnabled = !state.running
        qualityInputLayout.isEnabled = !state.running && modeGroup.checkedButtonId == modeVideoButton.id
        sessionButton.isEnabled = !state.running && !sessionBusy
        deleteSessionButton.isEnabled = !state.running && !sessionBusy
        retryButton.visibility = if (!state.running && state.retryConfig != null) View.VISIBLE else View.GONE
        retryButton.isEnabled = !sessionBusy
    }

    private fun startDownload() {
        val urls = UrlTools.extract(binding.urlInput.text?.toString().orEmpty())
        if (urls.isEmpty()) {
            binding.urlInputLayout.error = "Desteklenen platformlara ait geçerli bir HTTPS bağlantısı ekleyin."
            return
        }
        if (urls.size > 100) {
            binding.urlInputLayout.error = "Tek seferde en fazla 100 bağlantı ekleyebilirsiniz."
            return
        }
        binding.urlInputLayout.error = null
        saveDownloadSettings()
        launchDownload(
            DownloadConfig(urls, selectedMode(), binding.qualityInput.text.toString(), binding.playlistCheck.isChecked),
        )
    }

    private fun launchDownload(config: DownloadConfig) {
        if (DownloadStateStore.state.value.running) {
            toast("Bir indirme zaten devam ediyor.")
            return
        }
        val serviceIntent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putStringArrayListExtra(DownloadService.EXTRA_URLS, ArrayList(config.urls))
            putExtra(DownloadService.EXTRA_MODE, config.mode.name)
            putExtra(DownloadService.EXTRA_QUALITY, config.quality)
            putExtra(DownloadService.EXTRA_COLLECTION, config.includeCollection)
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (_: Exception) {
            DownloadStateStore.fail("İndirme başlatılamadı. Uygulamayı açık tutup yeniden deneyin.", config)
            return
        }
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
        val platforms = UrlTools.extract(binding.urlInput.text?.toString().orEmpty())
            .mapNotNull(SessionPlatform::fromUrl).distinct()
        if (platforms.size == 1) {
            startActivity(LoginActivity.intent(this, platforms.first()))
        } else {
            choosePlatform(SessionPlatform.entries.toList(), "Hangi platformda oturum açalım?") {
                startActivity(LoginActivity.intent(this, it))
            }
        }
    }

    private fun deleteSession() {
        val saved = SessionPlatform.entries.filter { SessionCookieStore.hasSession(this, it) }
        if (saved.isEmpty()) {
            toast("Silinecek kayıtlı oturum yok.")
            return
        }
        val selected = currentPlatform()?.takeIf { it in saved }
        if (selected != null) removeSession(selected)
        else choosePlatform(saved, "Silinecek oturumu seçin", ::removeSession)
    }

    private fun removeSession(platform: SessionPlatform) {
        sessionBusy = true
        render(DownloadStateStore.state.value)
        SessionCookieStore.delete(this, platform) { removed ->
            sessionBusy = false
            if (!isFinishing && !isDestroyed) {
                updateSessionStatus()
                render(DownloadStateStore.state.value)
                toast(if (removed) "${platform.displayName} oturumu silindi." else "Oturum dosyası silinemedi. Yeniden deneyin.")
            }
        }
    }

    private fun choosePlatform(platforms: List<SessionPlatform>, title: String, select: (SessionPlatform) -> Unit) {
        val labels = platforms.map {
            it.displayName + if (SessionCookieStore.hasSession(this, it)) " • Kayıtlı" else ""
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, index -> select(platforms[index]) }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun updateSessionStatus() {
        if (!::binding.isInitialized) return
        val platform = currentPlatform()
        val hasSession = platform?.let { SessionCookieStore.hasSession(this, it) } == true
        val saved = SessionPlatform.entries.filter { SessionCookieStore.hasSession(this, it) }
        binding.sessionStatus.text = when {
            platform == null && saved.isNotEmpty() -> "Kayıtlı: ${saved.joinToString { it.displayName }}"
            platform == null -> getString(R.string.session_none)
            hasSession -> getString(R.string.session_saved, platform.displayName)
            else -> "${platform.displayName} için oturum gerekirse giriş yapın"
        }
        binding.deleteSessionButton.visibility = if (hasSession || (platform == null && saved.isNotEmpty())) View.VISIBLE else View.GONE
    }

    private fun currentPlatform(): SessionPlatform? {
        val url = UrlTools.extract(binding.urlInput.text?.toString().orEmpty()).firstOrNull() ?: return null
        return SessionPlatform.fromUrl(url)
    }

    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (shared.isNotBlank()) {
            if (DownloadStateStore.state.value.running) {
                toast("İndirme devam ediyor. Yeni bağlantıyı işlem bitince paylaşın.")
                return
            }
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
        AppAppearance.applyWindow(this@MainActivity, theme)
        root.setBackgroundColor(theme.background)
        rootContainer.setBackgroundColor(theme.background)
        listOf(linkPanel, settingsPanel, statusPanel).forEach {
            it.background = rounded(theme.panel, theme.border, 16)
        }
        sessionPanel.background = rounded(theme.panelAlt, theme.border, 12)
        watermarkNote.background = rounded(theme.accentDark, theme.accent, 12)

        listOf(appTitle, statusText, sessionTitle).forEach { it.setTextColor(theme.primaryText) }
        listOf(appSubtitle, statusDetail, sessionStatus, playlistCheck, linkCount).forEach { it.setTextColor(theme.secondaryText) }
        listOf(platformText, logText, legalText, engineVersion).forEach { it.setTextColor(theme.faintText) }
        listOf(linksTitle, settingsTitle, watermarkNote).forEach { it.setTextColor(theme.accent) }

        styleInput(urlInputLayout, theme)
        styleInput(themeInputLayout, theme)
        styleInput(qualityInputLayout, theme)
        listOf(urlInput, themeInput, qualityInput).forEach {
            it.setTextColor(theme.primaryText)
            it.setHintTextColor(theme.faintText)
        }
        listOf(themeInput, qualityInput).forEach {
            it.setDropDownBackgroundDrawable(rounded(theme.panelAlt, theme.border, 12))
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
        styleButton(retryButton, theme.accent, theme.accent, null)
        styleButton(downloadsButton, theme.accent, theme.border, null)
        styleButton(logToggleButton, theme.secondaryText, theme.border, null)
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
        input.setEndIconTintList(ColorStateList.valueOf(theme.secondaryText))
        input.setErrorTextColor(ColorStateList.valueOf(theme.error))
    }

    private fun themedAdapter(items: List<String>) = object : ArrayAdapter<String>(
        this, android.R.layout.simple_list_item_1, items,
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getView(position, convertView, parent).also { styleMenuRow(it) }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getDropDownView(position, convertView, parent).also { styleMenuRow(it) }

        private fun styleMenuRow(view: View) {
            val theme = AppPreferences.theme(this@MainActivity)
            (view as? TextView)?.setTextColor(theme.primaryText)
            view.setBackgroundColor(theme.panelAlt)
        }
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
        private const val PREF_QUALITY = "download_quality"
        private const val PREF_MODE = "download_mode"
        private const val PREF_COLLECTION = "download_collection"
    }
}
