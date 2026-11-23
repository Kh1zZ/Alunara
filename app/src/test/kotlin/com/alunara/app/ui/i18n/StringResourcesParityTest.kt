package com.alunara.app.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesParityTest {
    @Test
    fun englishAndIndonesianStrings_haveExactKeyParityAndLanguageSwitcherKeys() {
        val enFile = resolveResourceFile("src/main/res/values/strings.xml")
        val idFile = resolveResourceFile("src/main/res/values-in/strings.xml")

        val enKeys = parseStringResources(enFile)
        val idKeys = parseStringResources(idFile)

        assertTrue("English strings.xml should not be empty", enKeys.isNotEmpty())
        assertEquals(
            "Keys missing in values-in/strings.xml",
            emptySet<String>(),
            enKeys.keys - idKeys.keys,
        )
        assertEquals(
            "Keys missing in values/strings.xml",
            emptySet<String>(),
            idKeys.keys - enKeys.keys,
        )

        val requiredLanguageKeys =
            listOf(
                "language_section_title",
                "language_subtitle",
                "language_option_indonesian",
                "language_option_english",
                "language_desc_indonesian",
                "language_desc_english",
                "login_feedback_cookie_applied",
                "login_feedback_email_sent",
                "login_feedback_password_sent",
                "login_feedback_text_sent",
                "login_feedback_next_clicked",
                "login_feedback_focus_up",
                "login_feedback_focus_down",
                "login_feedback_reloaded",
            )
        requiredLanguageKeys.forEach { key ->
            assertTrue("EN missing $key", !enKeys[key].isNullOrBlank())
            assertTrue("ID missing $key", !idKeys[key].isNullOrBlank())
        }
        assertEquals("Bahasa Indonesia", enKeys["language_option_indonesian"])
        assertEquals("Bahasa Indonesia", idKeys["language_option_indonesian"])
        assertEquals("English", enKeys["language_option_english"])
        assertEquals("English", idKeys["language_option_english"])
    }

    private fun resolveResourceFile(relativePath: String): File {
        val candidates =
            listOf(
                File(relativePath),
                File("app/$relativePath"),
            )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate resource file $relativePath from ${File(".").absolutePath}")
    }

    private fun parseStringResources(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = LinkedHashMap<String, String>(nodes.length)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            map[name] = node.textContent.orEmpty()
        }
        return map
    }
}
