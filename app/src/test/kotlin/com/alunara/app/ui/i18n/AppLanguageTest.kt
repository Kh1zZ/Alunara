package com.alunara.app.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun fromStorageKey_normalizesModernAndLegacyLanguageCodes() {
        val usLocale = Locale.forLanguageTag("en-US")
        val idLocale = Locale.forLanguageTag("id-ID")

        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromStorageKey("id", fallbackLocale = usLocale))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromStorageKey("in", fallbackLocale = usLocale))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromStorageKey("in_ID", fallbackLocale = usLocale))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromStorageKey("ID-id", fallbackLocale = usLocale))

        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageKey("en", fallbackLocale = idLocale))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageKey("en-US", fallbackLocale = idLocale))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageKey("EN_gb", fallbackLocale = idLocale))

        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromStorageKey("ja", fallbackLocale = usLocale))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromStorageKey("ja-JP", fallbackLocale = usLocale))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromStorageKey("jp_JP", fallbackLocale = usLocale))
    }

    @Test
    fun fromStorageKey_fallsBackToInitialLocaleWhenUnsetOrUnknown() {
        val idLocale = Locale.forLanguageTag("id-ID")
        val legacyInLocale = Locale.Builder().setLanguage("in").setRegion("ID").build()
        val usLocale = Locale.forLanguageTag("en-US")
        val jpLocale = Locale.forLanguageTag("ja-JP")
        val frLocale = Locale.forLanguageTag("fr-FR")
        val deLocale = Locale.forLanguageTag("de-DE")

        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromStorageKey(null, fallbackLocale = idLocale))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromStorageKey("", fallbackLocale = legacyInLocale))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageKey(null, fallbackLocale = usLocale))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromStorageKey(null, fallbackLocale = jpLocale))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageKey("pt", fallbackLocale = frLocale))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageKey("unknown", fallbackLocale = deLocale))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromStorageKey("unknown", fallbackLocale = idLocale))
    }

    @Test
    fun toLocale_preservesValidIsoRegionAndMapsLanguageCleanly() {
        val idRegion = Locale.forLanguageTag("id-ID")
        val enWithIdRegion = AppLocaleHelper.toLocale(AppLanguage.ENGLISH, preserveRegionFrom = idRegion)
        assertEquals("en", enWithIdRegion.language)
        assertEquals("ID", enWithIdRegion.country)

        val usRegion = Locale.forLanguageTag("en-US")
        val idWithUsRegion = AppLocaleHelper.toLocale(AppLanguage.INDONESIAN, preserveRegionFrom = usRegion)
        assertTrue(idWithUsRegion.language == "id" || idWithUsRegion.language == "in")
        assertEquals("US", idWithUsRegion.country)

        val jaWithIdRegion = AppLocaleHelper.toLocale(AppLanguage.JAPANESE, preserveRegionFrom = idRegion)
        assertEquals("ja", jaWithIdRegion.language)
        assertEquals("ID", jaWithIdRegion.country)

        val noRegion = AppLocaleHelper.toLocale(AppLanguage.ENGLISH, preserveRegionFrom = Locale.ROOT)
        assertEquals("en", noRegion.language)
        assertEquals("", noRegion.country)
    }

    @Test
    fun languageProperties_exposeExpectedInnerTubeHlDisplayNamesAndCycleOrder() {
        assertTrue(AppLanguage.INDONESIAN.isIndonesian)
        assertFalse(AppLanguage.ENGLISH.isIndonesian)
        assertFalse(AppLanguage.JAPANESE.isIndonesian)
        assertEquals("id", AppLanguage.INDONESIAN.innerTubeHl)
        assertEquals("en", AppLanguage.ENGLISH.innerTubeHl)
        assertEquals("ja", AppLanguage.JAPANESE.innerTubeHl)
        assertEquals("Bahasa Indonesia", AppLanguage.INDONESIAN.nativeDisplayName)
        assertEquals("English", AppLanguage.ENGLISH.nativeDisplayName)
        assertEquals("日本語", AppLanguage.JAPANESE.nativeDisplayName)

        assertEquals(AppLanguage.ENGLISH, AppLanguage.INDONESIAN.cycle(1))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.ENGLISH.cycle(1))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.JAPANESE.cycle(1))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.INDONESIAN.cycle(-1))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.ENGLISH.cycle(-1))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.JAPANESE.cycle(-1))
    }
}
