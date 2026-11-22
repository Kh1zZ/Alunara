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
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;OK"))
    }

    @Test
    fun hasValidSapisidCookie_acceptsStandardAndSecureCookies() {
        assertTrue(LocalLoginCompanionServer.hasValidSapisidCookie("SID=abc; SAPISID=xyz; HSID=1"))
        assertTrue(LocalLoginCompanionServer.hasValidSapisidCookie("__Secure-3PAPISID=tok"))
        assertFalse(LocalLoginCompanionServer.hasValidSapisidCookie("VISITOR_INFO1_LIVE=123"))
    }
}
