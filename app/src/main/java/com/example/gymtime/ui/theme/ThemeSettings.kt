package com.example.gymtime.ui.theme

enum class ThemePreset(val storageKey: String) {
    SUMMER_SHRED("summer_shred"),
    OCEAN_DRIVE("ocean_drive"),
    GRAPHITE_PUNCH("graphite_punch");

    companion object {
        fun fromStorageKey(value: String): ThemePreset {
            return entries.firstOrNull { it.storageKey == value } ?: SUMMER_SHRED
        }
    }
}

enum class ThemeFontOption(val storageKey: String, val displayName: String) {
    BEBAS_NEUE("bebas_neue", "Bebas Neue"),
    OSWALD("oswald", "Oswald"),
    RALEWAY("raleway", "Raleway"),
    SPACE_GROTESK("space_grotesk", "Space Grotesk"),
    PACIFICO("pacifico", "Pacifico"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromStorageKey(value: String): ThemeFontOption {
            return entries.firstOrNull { it.storageKey == value } ?: BEBAS_NEUE
        }
    }
}

data class AppThemeSettings(
    val preset: ThemePreset,
    val customAccentHex: String? = null,
    val fontOption: ThemeFontOption = ThemeFontOption.BEBAS_NEUE,
    val customFontUri: String? = null
) {
    val usesCustomColor: Boolean
        get() = !customAccentHex.isNullOrBlank()
}
