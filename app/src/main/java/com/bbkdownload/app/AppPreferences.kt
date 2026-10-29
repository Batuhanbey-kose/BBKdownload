package com.bbkdownload.app

import android.content.Context
import androidx.core.content.edit

object AppPreferences {
    // Keep the original activity preference file so existing theme selections survive.
    fun store(context: Context) = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    fun theme(context: Context): AppThemePalette = AppThemes.byId(store(context).getString("theme_id", null))

    fun saveTheme(context: Context, theme: AppThemePalette) {
        store(context).edit { putString("theme_id", theme.id) }
    }
}
