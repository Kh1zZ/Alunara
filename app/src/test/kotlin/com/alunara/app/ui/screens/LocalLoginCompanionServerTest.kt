package com.alunara.app.ui.screens

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLoginCompanionServerTest {
    @Test
    fun buildStatusJson_reflectsActiveStepAndEscapesQuotes() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val server = LocalLoginCompanionServer(scope = scope) {}
            server.currentStep = LocalLoginCompanionServer.STEP_PASSWORD
            server.currentHost = "accounts.google.com"
            server.lastStatusHint = """Langkah 2: Isi "Sandi""""

            val json = server.buildStatusJson()
            assertTrue(json.contains(""""step":"password""""))
            assertTrue(json.contains(""""host":"accounts.google.com""""))
            assertTrue(json.contains("""\"Sandi\""""))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun buildMobileHtml_containsSmartWizardStepsRemoteAndCookieModeAndEscapesBanner() {
        val html =
            LocalLoginCompanionServer.buildMobileHtml(
                token = "ABC123",
                statusMessage = "<script>alert(1)</script>OK",
                isError = false,
            )

        assertTrue(html.contains("Wizard Login"))
        assertTrue(html.contains("/status?t="))
        assertTrue(html.contains("prev_focus"))
        assertTrue(html.contains("next_focus"))
        assertTrue(html.contains("Paste Cookie"))
        assertTrue(html.contains("wizardForm"))
        assertTrue(html.contains("burstPoll"))
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;OK"))
    }

    @Test
    fun buildStatusJson_tracksStepTransitionsSynchronously() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val server = LocalLoginCompanionServer(scope = scope) {}
            assertTrue(server.buildStatusJson().contains(""""step":"email""""))

            server.currentStep = LocalLoginCompanionServer.STEP_PASSWORD
            server.lastStatusHint = "Langkah 2 dari 3 · Masukkan Kata Sandi"
            assertTrue(server.buildStatusJson().contains(""""step":"password""""))

            server.currentStep = LocalLoginCompanionServer.STEP_VERIFY
            server.lastStatusHint = "Langkah 3 dari 3 · Konfirmasi Google Prompt"
            assertTrue(server.buildStatusJson().contains(""""step":"verify""""))

            server.currentStep = LocalLoginCompanionServer.STEP_DONE
            assertTrue(server.buildStatusJson().contains(""""step":"done""""))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun hasValidSapisidCookie_acceptsStandardAndSecureCookies() {
        assertTrue(LocalLoginCompanionServer.hasValidSapisidCookie("SID=abc; SAPISID=xyz; HSID=1"))
        assertTrue(LocalLoginCompanionServer.hasValidSapisidCookie("__Secure-3PAPISID=tok"))
        assertFalse(LocalLoginCompanionServer.hasValidSapisidCookie("VISITOR_INFO1_LIVE=123"))
    }

    @Test
    fun buildMobileHtml_supportsEnglishLocalizationAndDefaultHint() {
        val html =
            LocalLoginCompanionServer.buildMobileHtml(
                token = "EN789",
                statusMessage = "Email sent",
                isError = false,
                isIndonesian = false,
            )

        assertTrue(html.contains("""<html lang="en">"""))
        assertTrue(html.contains("Alunara TV · Login Wizard"))
        assertTrue(html.contains("Step 1 · Google Email"))
        assertTrue(html.contains("Send Email &amp; Continue"))
        assertTrue(html.contains("Active on TV"))
        assertTrue(html.contains("Done ✓"))
        assertFalse(html.contains("Wizard Login"))
        assertFalse(html.contains("Langkah 1 · Email Google"))
    }

    @Test
    fun serverInitialHint_matchesRequestedLanguage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val idServer = LocalLoginCompanionServer(scope = scope, isIndonesian = true) {}
            assertTrue(idServer.lastStatusHint.contains("Langkah 1"))

            val enServer = LocalLoginCompanionServer(scope = scope, isIndonesian = false) {}
            assertTrue(enServer.lastStatusHint.contains("Step 1"))
        } finally {
            scope.cancel()
        }
    }
}
