package com.vuegram.data.login

import android.content.Context
import com.vuegram.BuildConfig

/**
 * The viewer's own Telegram API ID and hash (from my.telegram.org), which TDLib needs before anything else. Kept in
 * the app's private storage, which Android never backs up. Debug builds may take them from local.properties instead.
 */
object ApiKeys {
    private const val PREFS = "telegram_api_credentials"
    private const val ID = "api_id"
    private const val HASH = "api_hash"

    data class Keys(val id: Int, val hash: String)

    /** The saved keys (or a debug build's own), or null when the viewer still has to enter them. */
    fun get(context: Context): Keys? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(ID, null)?.toIntOrNull() ?: BuildConfig.TELEGRAM_API_ID.toIntOrNull()
        val hash = prefs.getString(HASH, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.TELEGRAM_API_HASH
        return if (id != null && id > 0 && isHash(hash)) Keys(id, hash) else null
    }

    /** Saves [id] and [hash] if they look right (see [problem]); returns whether they were saved. */
    fun save(context: Context, id: String, hash: String): Boolean {
        if (problem(id, hash) != null) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ID, id.trim())
            .putString(HASH, hash.trim().lowercase())
            .commit()
        return true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    enum class Problem { Id, Hash }

    /**
     * What is wrong with a typed or pasted pair, or null when it looks right: the API ID is a number, the hash 32
     * hexadecimal characters. Telegram itself decides whether they are valid, later.
     */
    fun problem(id: String, hash: String): Problem? = when {
        id.trim().let { it.isEmpty() || it.length > 12 || !it.all(Char::isDigit) || it.toLongOrNull()?.let { n -> n <= 0 || n > Int.MAX_VALUE } != false } -> Problem.Id
        !isHash(hash.trim()) -> Problem.Hash
        else -> null
    }

    private fun isHash(hash: String) = hash.length == 32 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
