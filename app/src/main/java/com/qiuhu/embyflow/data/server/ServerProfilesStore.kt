package com.qiuhu.embyflow.data.server

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qiuhu.embyflow.model.ServerProfile
import com.qiuhu.embyflow.model.ServerProfilesState
import com.qiuhu.embyflow.model.displayName
import com.qiuhu.embyflow.model.normalized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.serverProfilesDataStore: DataStore<Preferences> by preferencesDataStore(name = "server_profiles")

class ServerProfilesStore(
    context: Context,
) {
    private val dataStore = context.serverProfilesDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<ServerProfilesState> = dataStore.data.map { preferences ->
        val profiles = preferences[Keys.Profiles]
            ?.let(::decodeProfiles)
            .orEmpty()
            .map { it.normalized() }
        val activeId = preferences[Keys.ActiveProfileId]
        ServerProfilesState(
            profiles = profiles,
            activeProfileId = activeId.takeIf { id -> profiles.any { it.id == id } },
        )
    }

    suspend fun currentState(): ServerProfilesState = state.first()

    suspend fun ensureSeeded(
        defaultUrl: String,
        defaultUsername: String,
        defaultPassword: String,
    ) {
        dataStore.edit { preferences ->
            if (preferences[Keys.SeedCompleted] == true) {
                return@edit
            }

            val currentProfiles = preferences[Keys.Profiles]
                ?.let(::decodeProfiles)
                .orEmpty()
                .map { it.normalized() }
            if (currentProfiles.isNotEmpty()) {
                preferences[Keys.SeedCompleted] = true
                return@edit
            }

            val normalizedUrl = defaultUrl.trim().removeSuffix("/")
            val normalizedUsername = defaultUsername.trim()
            val normalizedPassword = defaultPassword.trim()
            if (normalizedUrl.isBlank() || normalizedUsername.isBlank() || normalizedPassword.isBlank()) {
                preferences[Keys.SeedCompleted] = true
                return@edit
            }

            val seeded = ServerProfile(
                id = UUID.randomUUID().toString(),
                name = "",
                serverUrl = normalizedUrl,
                username = normalizedUsername,
                password = normalizedPassword,
            )
            preferences[Keys.Profiles] = json.encodeToString(listOf(seeded))
            preferences[Keys.ActiveProfileId] = seeded.id
            preferences[Keys.SeedCompleted] = true
        }
    }

    suspend fun upsert(
        profile: ServerProfile,
        makeActive: Boolean,
    ) {
        val normalized = profile.normalized()
        dataStore.edit { preferences ->
            val current = preferences[Keys.Profiles]
                ?.let(::decodeProfiles)
                .orEmpty()
                .map { it.normalized() }
                .toMutableList()

            val index = current.indexOfFirst { it.id == normalized.id }
            val resolved = normalized.copy(
                id = normalized.id.ifBlank { UUID.randomUUID().toString() },
                name = normalized.name.ifBlank { normalized.displayName() },
            )

            if (index >= 0) {
                current[index] = resolved
            } else {
                current += resolved
            }

            preferences[Keys.Profiles] = json.encodeToString(current)
            if (makeActive || preferences[Keys.ActiveProfileId].isNullOrBlank()) {
                preferences[Keys.ActiveProfileId] = resolved.id
            }
        }
    }

    suspend fun setActive(profileId: String) {
        dataStore.edit { preferences ->
            val profiles = preferences[Keys.Profiles]
                ?.let(::decodeProfiles)
                .orEmpty()
            if (profiles.any { it.id == profileId }) {
                preferences[Keys.ActiveProfileId] = profileId
            }
        }
    }

    suspend fun delete(profileId: String) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.Profiles]
                ?.let(::decodeProfiles)
                .orEmpty()
                .filterNot { it.id == profileId }
            if (current.isEmpty()) {
                preferences.remove(Keys.Profiles)
                preferences.remove(Keys.ActiveProfileId)
                return@edit
            }

            preferences[Keys.Profiles] = json.encodeToString(current)
            val activeId = preferences[Keys.ActiveProfileId]
            if (activeId == profileId || current.none { it.id == activeId }) {
                preferences[Keys.ActiveProfileId] = current.first().id
            }
        }
    }

    private fun decodeProfiles(value: String): List<ServerProfile> = runCatching {
        json.decodeFromString<List<ServerProfile>>(value)
    }.getOrDefault(emptyList())

    private object Keys {
        val Profiles = stringPreferencesKey("profiles")
        val ActiveProfileId = stringPreferencesKey("active_profile_id")
        val SeedCompleted = androidx.datastore.preferences.core.booleanPreferencesKey("seed_completed")
    }
}
