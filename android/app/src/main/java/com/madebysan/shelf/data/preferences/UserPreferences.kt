package com.madebysan.shelf.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shelf_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val ID_TOKEN = stringPreferencesKey("id_token")
        val LIBRARY_FOLDER_ID = stringPreferencesKey("library_folder_id")
        val LIBRARY_FOLDER_NAME = stringPreferencesKey("library_folder_name")
        val THEME_MODE = intPreferencesKey("theme_mode") // 0=System, 1=Light, 2=Dark
        val LIBRARY_CUSTOM_NAME = stringPreferencesKey("library_custom_name")
    }

    val userId: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ID] }
    val userName: Flow<String?> = context.dataStore.data.map { it[Keys.USER_NAME] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[Keys.USER_EMAIL] }
    val idToken: Flow<String?> = context.dataStore.data.map { it[Keys.ID_TOKEN] }
    val libraryFolderId: Flow<String?> = context.dataStore.data.map { it[Keys.LIBRARY_FOLDER_ID] }
    val libraryFolderName: Flow<String?> = context.dataStore.data.map { it[Keys.LIBRARY_FOLDER_NAME] }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: 0 }
    val libraryCustomName: Flow<String?> = context.dataStore.data.map { it[Keys.LIBRARY_CUSTOM_NAME] }

    suspend fun saveUser(id: String, name: String?, email: String?, idToken: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = id
            name?.let { prefs[Keys.USER_NAME] = it }
            email?.let { prefs[Keys.USER_EMAIL] = it }
            prefs[Keys.ID_TOKEN] = idToken
        }
    }

    suspend fun saveLibraryFolder(folderId: String, folderName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_FOLDER_ID] = folderId
            prefs[Keys.LIBRARY_FOLDER_NAME] = folderName
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setLibraryCustomName(name: String?) {
        context.dataStore.edit { prefs ->
            if (name.isNullOrBlank()) {
                prefs.remove(Keys.LIBRARY_CUSTOM_NAME)
            } else {
                prefs[Keys.LIBRARY_CUSTOM_NAME] = name
            }
        }
    }

    suspend fun clearUser() {
        context.dataStore.edit { it.clear() }
    }
}
