package io.github.ct4nk3r.games.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "game_data")

/** Stores serializable values as JSON strings. Everything stays on the device. */
class KeyValueStore(context: Context) {
    private val dataStore = context.applicationContext.gameDataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun <T> observe(key: String, serializer: KSerializer<T>, default: T): Flow<T> {
        val prefKey = stringPreferencesKey(key)
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[prefKey]?.let { decode(it, serializer) } ?: default }
            .distinctUntilChanged()
    }

    suspend fun <T : Any> read(key: String, serializer: KSerializer<T>): T? {
        val prefKey = stringPreferencesKey(key)
        val prefs = dataStore.data.catch { emit(emptyPreferences()) }.first()
        return prefs[prefKey]?.let { decode(it, serializer) }
    }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        val encoded = json.encodeToString(serializer, value)
        dataStore.edit { it[stringPreferencesKey(key)] = encoded }
    }

    suspend fun <T> update(key: String, serializer: KSerializer<T>, default: T, transform: (T) -> T) {
        val prefKey = stringPreferencesKey(key)
        dataStore.edit { prefs ->
            val current = prefs[prefKey]?.let { decode(it, serializer) } ?: default
            prefs[prefKey] = json.encodeToString(serializer, transform(current))
        }
    }

    suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    // A value written by an older app version may no longer decode; fall back to the default.
    private fun <T> decode(raw: String, serializer: KSerializer<T>): T? =
        runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
}
