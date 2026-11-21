package com.alunara.core.data.store

import com.alunara.core.domain.model.AccountCredentials
import com.alunara.core.domain.model.AccountProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class KeystoreAccountSessionStoreTest {
    private val xorCipher =
        object : SessionPayloadCipher {
            override fun encrypt(plaintext: ByteArray): ByteArray =
                plaintext.mapIndexed { index, byte -> (byte.toInt() xor (0x5A + index % 13)).toByte() }.toByteArray()

            override fun decrypt(ciphertext: ByteArray): ByteArray = encrypt(ciphertext)
        }

    @Test
    fun saveLoadAndClearSession_roundTripsEncryptedCredentialsWithoutPlaintextFile() =
        runBlocking {
            val dir = createTempDirectory("alunara_session_test").toFile()
            try {
                val sessionFile = File(dir, "alunara_session.enc")
                val store = KeystoreAccountSessionStore(sessionFile, xorCipher)

                assertNull(store.loadSession())

                val profile =
                    AccountProfile(
                        accountId = "@alunara",
                        name = "Alunara TV",
                        email = "tv@example.com",
                        channelHandle = "@alunara",
                        avatarUrl = "https://img/avatar.png",
                    )
                val credentials =
                    AccountCredentials(
                        cookie = "SAPISID=secret_value; SID=sid_value",
                        dataSyncId = "12345||67890",
                        visitorData = "visitor-abc",
                        poToken = "po-xyz",
                    )

                store.saveSession(profile, credentials)
                assertTrue(sessionFile.exists())
                val rawOnDisk = sessionFile.readText(Charsets.UTF_8)
                assertFalse(rawOnDisk.contains("secret_value"))
                assertFalse(rawOnDisk.contains("Alunara TV"))

                val loaded = store.loadSession()
                assertNotNull(loaded)
                assertEquals(profile, loaded?.first)
                assertEquals(credentials, loaded?.second)

                // Corrupted payload triggers safe wipe rather than crashing
                sessionFile.writeText("not-valid-base64!!!", Charsets.UTF_8)
                assertNull(store.loadSession())
                assertFalse(sessionFile.exists())

                store.saveSession(profile, credentials)
                assertTrue(sessionFile.exists())
                store.clearSession()
                assertFalse(sessionFile.exists())
                assertNull(store.loadSession())
            } finally {
                dir.deleteRecursively()
            }
        }
}
