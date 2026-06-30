package com.momin.pipviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "library")

/**
 * Persists the user's chosen root folders and their starred (★) folders.
 *
 * Everything is stored as encoded [FolderRef] strings inside two preference string-sets, so there
 * are no database/migration concerns and reads are cheap.
 */
class FolderRepository(private val context: Context) {

    private val rootsKey = stringSetPreferencesKey("roots")
    private val starredKey = stringSetPreferencesKey("starred")

    val roots: Flow<List<FolderRef>> = context.dataStore.data.map { prefs ->
        prefs[rootsKey].orEmpty().mapNotNull(FolderRef::decode)
            .sortedBy { it.name.lowercase() }
    }

    val starred: Flow<List<FolderRef>> = context.dataStore.data.map { prefs ->
        prefs[starredKey].orEmpty().mapNotNull(FolderRef::decode)
            .sortedBy { it.name.lowercase() }
    }

    /** Set of [FolderRef.key]s that are starred — used for fast membership checks in the UI. */
    val starredKeys: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[starredKey].orEmpty().mapNotNull(FolderRef::decode).map { it.key }.toSet()
    }

    suspend fun addRoot(folder: FolderRef) {
        context.dataStore.edit { prefs ->
            val current = prefs[rootsKey].orEmpty()
                .mapNotNull(FolderRef::decode)
                .filterNot { it.key == folder.key }
            prefs[rootsKey] = (current + folder).map { it.encode() }.toSet()
        }
    }

    suspend fun removeRoot(folder: FolderRef) {
        context.dataStore.edit { prefs ->
            prefs[rootsKey] = prefs[rootsKey].orEmpty()
                .mapNotNull(FolderRef::decode)
                .filterNot { it.key == folder.key }
                .map { it.encode() }
                .toSet()
            // Removing a root also clears any star that pointed at it.
            prefs[starredKey] = prefs[starredKey].orEmpty()
                .mapNotNull(FolderRef::decode)
                .filterNot { it.key == folder.key }
                .map { it.encode() }
                .toSet()
        }
    }

    suspend fun setStarred(folder: FolderRef, starred: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[starredKey].orEmpty()
                .mapNotNull(FolderRef::decode)
                .filterNot { it.key == folder.key }
            prefs[starredKey] = if (starred) {
                (current + folder).map { it.encode() }.toSet()
            } else {
                current.map { it.encode() }.toSet()
            }
        }
    }
}
