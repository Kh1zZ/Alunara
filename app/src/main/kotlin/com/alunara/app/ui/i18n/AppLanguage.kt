package com.alunara.app.ui.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * Supported in-app UI & content languages managed directly inside Alunara Settings.
 * Adding a new language only requires adding an enum entry and its corresponding `res/values-<lang>/strings.xml`.
 */
@Immutable
enum class AppLanguage(
    val storageKey: String,
    val bcp47Tag: String,
    val innerTubeHl: String,
) {
    INDONESIAN(
        storageKey = "id",
        bcp47Tag = "id",
        innerTubeHl = "id",
    ),
    ENGLISH(
        storageKey = "en",
        bcp47Tag = "en",
        innerTubeHl = "en",
    ),
    ;

    val isIndonesian: Boolean
        get() = this == INDONESIAN

    companion object {
        fun defaultForLocale(locale: Locale = Locale.getDefault()): AppLanguage {
            val lang = locale.language.lowercase(Locale.ROOT)
            return if (lang == "id" || lang == "in") INDONESIAN else ENGLISH
        }

        fun fromStorageKey(
            raw: String?,
            fallbackLocale: Locale = Locale.getDefault(),
        ): AppLanguage {
            val normalized = raw?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')
            if (normalized.isNullOrEmpty()) {
                return defaultForLocale(fallbackLocale)
            }
            val primary = normalized.substringBefore('-')
            return when (primary) {
                "id", "in" -> INDONESIAN
                "en" -> ENGLISH
                else -> defaultForLocale(fallbackLocale)
            }
        }
    }
}

object AppLocaleHelper {
    fun toLocale(
        language: AppLanguage,
        preserveRegionFrom: Locale? = null,
    ): Locale {
        val builder = Locale.Builder().setLanguage(language.bcp47Tag)
        val region = preserveRegionFrom?.country?.trim().orEmpty()
        if (isValidIsoRegion(region)) {
            builder.setRegion(region)
        }
        return builder.build()
    }

    fun wrapContext(
        base: Context,
        language: AppLanguage,
    ): Context {
        val baseConfig = base.resources.configuration
        val currentLocale =
            if (!baseConfig.locales.isEmpty) {
                baseConfig.locales[0]
            } else {
                Locale.getDefault()
            }
        val targetLocale = toLocale(language, preserveRegionFrom = currentLocale)
        Locale.setDefault(targetLocale)
        val updatedConfig =
            Configuration(baseConfig).apply {
                setLocale(targetLocale)
                setLocales(LocaleList(targetLocale))
            }
        return base.createConfigurationContext(updatedConfig)
    }

    fun createLocalizedContext(
        activity: Context,
        baseConfig: Configuration,
        language: AppLanguage,
    ): Pair<Configuration, Context> {
        val currentLocale =
            if (!baseConfig.locales.isEmpty) {
                baseConfig.locales[0]
            } else {
                Locale.getDefault()
            }
        val targetLocale = toLocale(language, preserveRegionFrom = currentLocale)
        val updatedConfig =
            Configuration(baseConfig).apply {
                setLocale(targetLocale)
                setLocales(LocaleList(targetLocale))
            }
        val configContext = activity.createConfigurationContext(updatedConfig)
        val localizedResources = configContext.resources
        val wrappedContext =
            object : ContextWrapper(activity) {
                override fun getResources(): Resources = localizedResources

                override fun getAssets(): AssetManager = localizedResources.assets
            }
        return updatedConfig to wrappedContext
    }

    private fun isValidIsoRegion(region: String): Boolean =
        (region.length == 2 && region.all { it in 'A'..'Z' || it in 'a'..'z' }) ||
            (region.length == 3 && region.all { it in '0'..'9' })
}

/**
 * Lightweight SharedPreferences store safe to read from [android.app.Activity.attachBaseContext]
 * prior to [android.app.Application.onCreate] and [com.alunara.app.AppContainer] initialization.
 */
class AlunaraLanguageStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(fallbackLocale: Locale = Locale.getDefault()): AppLanguage =
        AppLanguage.fromStorageKey(prefs.getString(KEY_APP_LANGUAGE, null), fallbackLocale)

    fun save(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.storageKey).apply()
    }

    companion object {
        private const val PREFS_NAME = "alunara_language_prefs"
        private const val KEY_APP_LANGUAGE = "app_language"
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }
