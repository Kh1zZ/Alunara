package com.alunara.app.remote

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

interface RemotePreferencesStore {
    fun isRemoteEnabled(): Boolean

    fun setRemoteEnabled(enabled: Boolean)

    fun getOrCreatePairPin(): Int

    fun getOrCreateSessionToken(): String

    fun regenerateCredentials(): Pair<Int, String>
}

class InMemoryRemotePreferencesStore(
    initialEnabled: Boolean = true,
    initialPin: Int = generatePin(),
    initialToken: String = generateToken(),
) : RemotePreferencesStore {
    private var enabled: Boolean = initialEnabled
    private var pairPin: Int = initialPin and 0xFFF
    private var sessionToken: String = initialToken

    @Synchronized
    override fun isRemoteEnabled(): Boolean = enabled

    @Synchronized
    override fun setRemoteEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    @Synchronized
    override fun getOrCreatePairPin(): Int = pairPin

    @Synchronized
    override fun getOrCreateSessionToken(): String = sessionToken

    @Synchronized
    override fun regenerateCredentials(): Pair<Int, String> {
        pairPin = generatePin()
        sessionToken = generateToken()
        return pairPin to sessionToken
    }

    companion object {
        private val random = SecureRandom()
        private const val TOKEN_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz"

        fun generatePin(): Int = random.nextInt(4096)

        fun generateToken(length: Int = 24): String =
            buildString(length) {
                repeat(length) {
                    append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)])
                }
            }
    }
}

class SharedPreferencesRemotePreferencesStore(
    context: Context,
) : RemotePreferencesStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun isRemoteEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    @Synchronized
    override fun setRemoteEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    @Synchronized
    override fun getOrCreatePairPin(): Int {
        val stored = prefs.getInt(KEY_PAIR_PIN, -1)
        if (stored in 0..4095) return stored
        val fresh = InMemoryRemotePreferencesStore.generatePin()
        prefs.edit().putInt(KEY_PAIR_PIN, fresh).apply()
        return fresh
    }

    @Synchronized
    override fun getOrCreateSessionToken(): String {
        val stored = prefs.getString(KEY_SESSION_TOKEN, null)?.takeIf { it.length >= 16 }
        if (stored != null) return stored
        val fresh = InMemoryRemotePreferencesStore.generateToken()
        prefs.edit().putString(KEY_SESSION_TOKEN, fresh).apply()
        return fresh
    }

    @Synchronized
    override fun regenerateCredentials(): Pair<Int, String> {
        val freshPin = InMemoryRemotePreferencesStore.generatePin()
        val freshToken = InMemoryRemotePreferencesStore.generateToken()
        prefs
            .edit()
            .putInt(KEY_PAIR_PIN, freshPin)
            .putString(KEY_SESSION_TOKEN, freshToken)
            .apply()
        return freshPin to freshToken
    }

    companion object {
        private const val PREFS_NAME = "alunara_remote_prefs"
        private const val KEY_ENABLED = "remote_enabled"
        private const val KEY_PAIR_PIN = "remote_pair_pin"
        private const val KEY_SESSION_TOKEN = "remote_session_token"
    }
}
