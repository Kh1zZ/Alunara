package com.alunara.app.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesParityTest {
    @Test
    fun allSupportedLocaleStrings_haveExactKeyPlaceholderParityAndLanguagePickerKeys() {
        val enFile = resolveResourceFile("src/main/res/values/strings.xml")
        val idFile = resolveResourceFile("src/main/res/values-in/strings.xml")
        val jaFile = resolveResourceFile("src/main/res/values-ja/strings.xml")

        val enKeys = parseStringResources(enFile)
        val idKeys = parseStringResources(idFile)
        val jaKeys = parseStringResources(jaFile)

        assertTrue("English strings.xml should not be empty", enKeys.isNotEmpty())
        assertEquals(
            "Keys missing in values-in/strings.xml",
            emptySet<String>(),
            enKeys.keys - idKeys.keys,
        )
        assertEquals(
            "Extra keys in values-in/strings.xml",
            emptySet<String>(),
            idKeys.keys - enKeys.keys,
        )
        assertEquals(
            "Keys missing in values-ja/strings.xml",
            emptySet<String>(),
            enKeys.keys - jaKeys.keys,
        )
        assertEquals(
            "Extra keys in values-ja/strings.xml",
            emptySet<String>(),
            jaKeys.keys - enKeys.keys,
        )

        val placeholderRegex = Regex("""%\d+\$[sd]""")
        enKeys.forEach { (key, enValue) ->
            val idValue = idKeys.getValue(key)
            val jaValue = jaKeys.getValue(key)
            assertTrue("EN blank $key", enValue.isNotBlank())
            assertTrue("ID blank $key", idValue.isNotBlank())
            assertTrue("JA blank $key", jaValue.isNotBlank())

            val expectedPlaceholders = placeholderRegex.findAll(enValue).map { it.value }.toList()
            val idPlaceholders = placeholderRegex.findAll(idValue).map { it.value }.toList()
            val jaPlaceholders = placeholderRegex.findAll(jaValue).map { it.value }.toList()
            assertEquals("Placeholder mismatch in ID for $key", expectedPlaceholders, idPlaceholders)
            assertEquals("Placeholder mismatch in JA for $key", expectedPlaceholders, jaPlaceholders)
        }

        val requiredLanguageKeys =
            listOf(
                "language_section_title",
                "language_subtitle",
                "language_option_indonesian",
                "language_option_english",
                "language_option_japanese",
                "language_desc_indonesian",
                "language_desc_english",
                "language_desc_japanese",
                "language_picker_action_hint",
                "language_picker_focus_hint",
                "language_picker_prev_cd",
                "language_picker_next_cd",
                "language_picker_done",
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
            assertTrue("JA missing $key", !jaKeys[key].isNullOrBlank())
        }
        assertEquals("Bahasa Indonesia", enKeys["language_option_indonesian"])
        assertEquals("Bahasa Indonesia", idKeys["language_option_indonesian"])
        assertEquals("Bahasa Indonesia", jaKeys["language_option_indonesian"])
        assertEquals("English", enKeys["language_option_english"])
        assertEquals("English", idKeys["language_option_english"])
        assertEquals("English", jaKeys["language_option_english"])
        assertEquals("日本語", enKeys["language_option_japanese"])
        assertEquals("日本語", idKeys["language_option_japanese"])
        assertEquals("日本語", jaKeys["language_option_japanese"])
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
