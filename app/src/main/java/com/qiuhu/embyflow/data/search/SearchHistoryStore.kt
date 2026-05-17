package com.qiuhu.embyflow.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

class SearchHistoryStore(
    context: Context,
) {
    private val dataStore = context.searchHistoryDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val queries: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[Keys.RecentQueries]
            ?.let(::decodeQueries)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?: emptyList()
    }

    suspend fun record(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return

        dataStore.edit { preferences ->
            val current = preferences[Keys.RecentQueries]
                ?.let(::decodeQueries)
                .orEmpty()
            val updated = buildList {
                add(normalized)
                current
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .filterNot { it.equals(normalized, ignoreCase = true) }
                    .take(9)
                    .forEach(::add)
            }
            preferences[Keys.RecentQueries] = json.encodeToString(updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.RecentQueries)
        }
    }

    private fun decodeQueries(value: String): List<String> = runCatching {
        json.decodeFromString<List<String>>(value)
    }.getOrDefault(emptyList())

    private object Keys {
        val RecentQueries = stringPreferencesKey("recent_queries")
    }
}
