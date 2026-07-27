package com.bbkdownload.app

import androidx.core.graphics.toColorInt

data class AppThemePalette(
    val id: String,
    val label: String,
    val background: Int,
    val panel: Int,
    val panelAlt: Int,
    val border: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val faintText: Int,
    val accent: Int,
    val accentDark: Int,
    val error: Int,
)

object AppThemes {
    val all = listOf(
        palette("neon", "Neon BBK", "#090D18", "#11182A", "#17213A", "#2A3A59", "#F8FAFC", "#94A3B8", "#64748B", "#22D3EE", "#164E63", "#FB7185"),
        palette("emerald", "Zümrüt", "#06130E", "#0B2218", "#123022", "#1F4A36", "#ECFDF5", "#A7F3D0", "#6EE7B7", "#34D399", "#065F46", "#F87171"),
        palette("violet", "Mor Gece", "#100A1F", "#1C1235", "#261A45", "#4C2D7A", "#F5F3FF", "#C4B5FD", "#8B5CF6", "#A78BFA", "#4C1D95", "#FB7185"),
        palette("sunset", "Gün Batımı", "#1C0B10", "#2A1218", "#371C20", "#713F12", "#FFF7ED", "#FDBA74", "#FB923C", "#F97316", "#7C2D12", "#F43F5E"),
        palette("ocean", "Okyanus", "#061423", "#0B2239", "#12314D", "#1D4E72", "#EFF6FF", "#BAE6FD", "#7DD3FC", "#38BDF8", "#075985", "#FB7185"),
        palette("rose", "Gül", "#1A0B13", "#2A111D", "#3A1828", "#7F1D4E", "#FFF1F2", "#FDA4AF", "#FB7185", "#F43F5E", "#881337", "#FACC15"),
        palette("mono", "Siyah Beyaz", "#050505", "#111111", "#1C1C1C", "#3A3A3A", "#FAFAFA", "#CFCFCF", "#8A8A8A", "#FFFFFF", "#4A4A4A", "#FF6B6B"),
        palette("matrix", "Matrix", "#020A05", "#06140B", "#0B1F12", "#14532D", "#DCFCE7", "#86EFAC", "#22C55E", "#4ADE80", "#166534", "#F97316"),
        palette("cyber", "Cyber Sarı", "#111005", "#1F1C0A", "#2D2810", "#665C16", "#FEFCE8", "#FDE68A", "#FACC15", "#EAB308", "#713F12", "#EF4444"),
        palette("ice", "Buz", "#06111A", "#0D1B2A", "#132A3E", "#2B5876", "#F0FDFA", "#A5F3FC", "#67E8F9", "#5EEAD4", "#155E75", "#F472B6"),
    )

    val default = all.first()

    fun byLabel(label: String): AppThemePalette = all.firstOrNull { it.label == label } ?: default

    fun byId(id: String?): AppThemePalette = all.firstOrNull { it.id == id } ?: default

    private fun palette(
        id: String,
        label: String,
        background: String,
        panel: String,
        panelAlt: String,
        border: String,
        primaryText: String,
        secondaryText: String,
        faintText: String,
        accent: String,
        accentDark: String,
        error: String,
    ) = AppThemePalette(
        id = id,
        label = label,
        background = background.toColorInt(),
        panel = panel.toColorInt(),
        panelAlt = panelAlt.toColorInt(),
        border = border.toColorInt(),
        primaryText = primaryText.toColorInt(),
        secondaryText = secondaryText.toColorInt(),
        faintText = faintText.toColorInt(),
        accent = accent.toColorInt(),
        accentDark = accentDark.toColorInt(),
        error = error.toColorInt(),
    )
}
