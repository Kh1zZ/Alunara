package com.alunara.app.ui.screens

import com.alunara.app.ui.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertTrue(html.contains("Tempel Cookie"))
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
                language = AppLanguage.ENGLISH,
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
    fun buildMobileHtml_supportsJapaneseLocalizationAndDefaultHint() {
        val html =
            LocalLoginCompanionServer.buildMobileHtml(
                token = "JA123",
                statusMessage = "メール送信済み",
                isError = false,
                language = AppLanguage.JAPANESE,
            )

        assertTrue(html.contains("""<html lang="ja">"""))
        assertTrue(html.contains("Alunara TV · ログインウィザード"))
        assertTrue(html.contains("ステップ 1 · Google メール"))
        assertTrue(html.contains("メールを送信して次へ"))
        assertTrue(html.contains("テレビで表示中"))
        assertTrue(html.contains("完了 ✓"))
        assertFalse(html.contains("Step 1 · Google Email"))
        assertFalse(html.contains("Langkah 1 · Email Google"))
    }

    @Test
    fun serverInitialHint_matchesRequestedLanguage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val idServer = LocalLoginCompanionServer(scope = scope, language = AppLanguage.INDONESIAN) {}
            assertTrue(idServer.lastStatusHint.contains("Langkah 1"))

            val enServer = LocalLoginCompanionServer(scope = scope, language = AppLanguage.ENGLISH) {}
            assertTrue(enServer.lastStatusHint.contains("Step 1"))

            val jaServer = LocalLoginCompanionServer(scope = scope, language = AppLanguage.JAPANESE) {}
            assertTrue(jaServer.lastStatusHint.contains("ステップ 1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun encodePairingCode_and_decodePairingCode_roundTripCompactAndFullFormats() {
        // 6-char compact code for 192.168.x.y on DEFAULT_PAIRING_PORT (4580), formatted XXXX-XX
        val homeCode = LocalLoginCompanionServer.encodePairingCode("192.168.1.42", 4580, 1234)
        assertNotNull(homeCode)
        assertEquals(6, homeCode!!.replace("-", "").length)
        val homeDecoded = LocalLoginCompanionServer.decodePairingCode(homeCode)
        assertEquals(
            DecodedPairingTarget("192.168.1.42", 4580, 1234),
            homeDecoded,
        )

        // 7-char compact code for 10.0..15.x.y and 172.16..31.x.y on port 4580, formatted XXXX-XXX
        val tenCode = LocalLoginCompanionServer.encodePairingCode("10.4.12.99", 4580, 77)
        assertNotNull(tenCode)
        assertEquals(7, tenCode!!.replace("-", "").length)
        assertEquals(
            DecodedPairingTarget("10.4.12.99", 4580, 77),
            LocalLoginCompanionServer.decodePairingCode(tenCode.lowercase()),
        )

        val vpnCode = LocalLoginCompanionServer.encodePairingCode("172.20.8.5", 4580, 4095)
        assertNotNull(vpnCode)
        assertEquals(7, vpnCode!!.replace("-", "").length)
        assertEquals(
            DecodedPairingTarget("172.20.8.5", 4580, 4095),
            LocalLoginCompanionServer.decodePairingCode(vpnCode),
        )

        // 12-char full code for arbitrary IPv4 or non-default port, formatted XXXX-XXXX-XXXX
        val customCode = LocalLoginCompanionServer.encodePairingCode("192.168.1.42", 52123, 987)
        assertNotNull(customCode)
        assertEquals(12, customCode!!.replace("-", "").length)
        assertEquals(
            DecodedPairingTarget("192.168.1.42", 52123, 987),
            LocalLoginCompanionServer.decodePairingCode(customCode),
        )
    }

    @Test
    fun markCompleted_updatesStatusJsonAndMobileHtmlContainsDoneModal() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val server = LocalLoginCompanionServer(scope = scope, language = AppLanguage.ENGLISH) {}
            assertFalse(server.isCompletedOnTv)

            server.markCompleted("""Alunara "TV"""")
            val json = server.buildStatusJson()
            assertTrue(server.isCompletedOnTv)
            assertTrue(json.contains(""""completed":true"""))
            assertTrue(json.contains(""""accountName":"Alunara \"TV\"""""))

            val html =
                LocalLoginCompanionServer.buildMobileHtml(
                    token = "PAIR01",
                    statusJson = json,
                    language = AppLanguage.ENGLISH,
                )
            assertTrue(html.contains("""id="doneModal""""))
            assertTrue(html.contains("showDoneModal("))
            assertTrue(html.contains("clearInterval(pollTimer)"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun inferStepFromUrl_doesNotTriggerDoneOnGoogleContinueQueryParam() {
        val googleLoginUrl =
            "https://accounts.google.com/v3/signin/identifier?continue=https://music.youtube.com/&ltmpl=music&service=youtube"
        assertFalse(isMusicYouTubeHost(googleLoginUrl))
        assertEquals("accounts.google.com", extractUrlHost(googleLoginUrl))
        val inferred = inferStepFromUrl(googleLoginUrl, AppLanguage.INDONESIAN)
        assertNotNull(inferred)
        assertEquals(LocalLoginCompanionServer.STEP_EMAIL, inferred!!.first)

        val pwdUrl =
            "https://accounts.google.com/v3/signin/challenge/pwd?continue=https://music.youtube.com/"
        assertFalse(isMusicYouTubeHost(pwdUrl))
        assertEquals(
            LocalLoginCompanionServer.STEP_PASSWORD,
            inferStepFromUrl(pwdUrl, AppLanguage.ENGLISH)?.first,
        )

        val ytmUrl = "https://music.youtube.com/?hl=en"
        assertTrue(isMusicYouTubeHost(ytmUrl))
        assertEquals(
            LocalLoginCompanionServer.STEP_VERIFYING,
            inferStepFromUrl(ytmUrl, AppLanguage.ENGLISH)?.first,
        )
    }

    @Test
    fun buildPortalQrUrl_and_verifyingOrErrorStates_syncCleanlyAndGuardDoneModal() {
        assertEquals(
            "https://kh1zz.github.io/Alunara/login/?c=K7M9-P2",
            LocalLoginCompanionServer.buildPortalQrUrl("K7M9-P2"),
        )
        assertEquals(null, LocalLoginCompanionServer.buildPortalQrUrl(""))

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val server = LocalLoginCompanionServer(scope = scope, language = AppLanguage.ENGLISH) {}
            server.markVerifying()
            val verifyingJson = server.buildStatusJson()
            assertTrue(server.isVerifyingOnTv)
            assertFalse(server.isCompletedOnTv)
            assertTrue(verifyingJson.contains(""""step":"verifying""""))
            assertTrue(verifyingJson.contains(""""verifying":true"""))
            assertTrue(verifyingJson.contains(""""completed":false"""))

            server.markVerificationFailed("Invalid credentials")
            val errorJson = server.buildStatusJson()
            assertFalse(server.isVerifyingOnTv)
            assertFalse(server.isCompletedOnTv)
            assertTrue(errorJson.contains(""""error":"Invalid credentials""""))

            val html =
                LocalLoginCompanionServer.buildMobileHtml(
                    token = "GUARD1",
                    statusJson = verifyingJson,
                    language = AppLanguage.ENGLISH,
                )
            assertTrue(html.contains("st.completed === true"))
            assertFalse(html.contains("st.step === 'done'"))
            assertTrue(html.contains("visibilitychange"))
        } finally {
            scope.cancel()
        }
    }
}

