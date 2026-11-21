package com.alunara.core.data.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.alunara.core.domain.contract.AccountSessionStore
import com.alunara.core.domain.model.AccountCredentials
import com.alunara.core.domain.model.AccountProfile
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionPayloadCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}

/**
 * Encrypts active account credentials via Android Keystore AES-256-GCM in a dedicated no-backup file.
 * Keeps plaintext secrets out of Room and logs (PRD §§8.2, 10.2).
 */
class AndroidKeystoreAesGcmCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SessionPayloadCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        require(iv.size == GCM_IV_LENGTH_BYTES) { "Unexpected GCM IV length: ${iv.size}" }
        val encrypted = cipher.doFinal(plaintext)
        return iv + encrypted
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > GCM_IV_LENGTH_BYTES) { "Corrupted encrypted session payload" }
        val iv = ciphertext.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encrypted = ciphertext.copyOfRange(GCM_IV_LENGTH_BYTES, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE_BITS)
                .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "alunara_account_session_aes256"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}

class KeystoreAccountSessionStore internal constructor(
    private val sessionFile: File,
    private val cipher: SessionPayloadCipher,
) : AccountSessionStore {
    constructor(context: Context) : this(
        sessionFile = File(context.noBackupFilesDir, SESSION_FILE_NAME),
        cipher = AndroidKeystoreAesGcmCipher(),
    )

    override suspend fun loadSession(): Pair<AccountProfile, AccountCredentials>? {
        if (!sessionFile.exists()) return null
        val raw =
            runCatching {
                val encoded = sessionFile.readText(Charsets.UTF_8).trim()
                val cipherBytes = Base64.getDecoder().decode(encoded)
                val plainBytes = cipher.decrypt(cipherBytes)
                decodePayload(plainBytes.toString(Charsets.UTF_8))
            }.getOrNull()
        if (raw == null) {
            clearSession()
        }
        return raw
    }

    override suspend fun saveSession(
        profile: AccountProfile,
        credentials: AccountCredentials,
    ) {
        sessionFile.parentFile?.mkdirs()
        val payload = encodePayload(profile, credentials).toByteArray(Charsets.UTF_8)
        val cipherBytes = cipher.encrypt(payload)
        sessionFile.writeText(Base64.getEncoder().encodeToString(cipherBytes), Charsets.UTF_8)
    }

    override suspend fun clearSession() {
        runCatching {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
        }
    }

    private fun encodePayload(
        profile: AccountProfile,
        credentials: AccountCredentials,
    ): String {
        val encoder = Base64.getEncoder()
        fun enc(value: String?): String =
            if (value == null) "-" else encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
        return listOf(
            "1",
            enc(profile.accountId),
            enc(profile.name),
            enc(profile.email),
            enc(profile.channelHandle),
            enc(profile.avatarUrl),
            enc(credentials.cookie),
            enc(credentials.dataSyncId),
            enc(credentials.visitorData),
            enc(credentials.poToken),
        ).joinToString(separator = "\n")
    }

    private fun decodePayload(raw: String): Pair<AccountProfile, AccountCredentials>? {
        val parts = raw.lines()
        if (parts.size < 10 || parts[0] != "1") return null
        val decoder = Base64.getDecoder()
        fun dec(token: String): String? =
            if (token == "-") null else String(decoder.decode(token), Charsets.UTF_8)

        val accountId = dec(parts[1])?.takeIf { it.isNotBlank() } ?: return null
        val name = dec(parts[2])?.takeIf { it.isNotBlank() } ?: return null
        val cookie = dec(parts[6])?.takeIf { it.isNotBlank() } ?: return null

        val profile =
            AccountProfile(
                accountId = accountId,
                name = name,
                email = dec(parts[3]),
                channelHandle = dec(parts[4]),
                avatarUrl = dec(parts[5]),
            )
        val credentials =
            AccountCredentials(
                cookie = cookie,
                dataSyncId = dec(parts[7]),
                visitorData = dec(parts[8]),
                poToken = dec(parts[9]),
            )
        return profile to credentials
    }

    private companion object {
        const val SESSION_FILE_NAME = "alunara_session.enc"
    }
}
