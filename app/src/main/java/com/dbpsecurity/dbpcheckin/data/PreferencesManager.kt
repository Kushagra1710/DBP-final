package com.dbpsecurity.dbpcheckin.data

import android.content.Context
import android.content.SharedPreferences
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dbp_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveProfile(profile: Profile) {
        prefs.edit().putString("cached_profile", json.encodeToString(profile)).apply()
        // Update session as well
        SessionManager.currentUserProfile = profile
    }

    fun getProfile(): Profile? {
        val jsonStr = prefs.getString("cached_profile", null) ?: return null
        return try {
            val profile = json.decodeFromString<Profile>(jsonStr)
            SessionManager.currentUserProfile = profile // Sync with session
            profile
        } catch (e: Exception) {
            null
        }
    }

    fun saveGroup(group: Group) {
        prefs.edit().putString("cached_group", json.encodeToString(group)).apply()
        SessionManager.currentUserGroup = group
    }

    fun getGroup(): Group? {
        val jsonStr = prefs.getString("cached_group", null) ?: return null
        return try {
            val group = json.decodeFromString<Group>(jsonStr)
            SessionManager.currentUserGroup = group
            group
        } catch (e: Exception) {
            null
        }
    }

    fun saveEmbedding(embedding: FloatArray) {
        // Convert FloatArray to comma-separated string
        val embeddingStr = embedding.joinToString(",")
        prefs.edit().putString("cached_embedding", embeddingStr).apply()
        SessionManager.currentReferenceEmbedding = embedding
    }

    fun getEmbedding(): FloatArray? {
        val embeddingStr = prefs.getString("cached_embedding", null) ?: return null
        return try {
            val list = embeddingStr.split(",").map { it.toFloat() }
            val array = list.toFloatArray()
            SessionManager.currentReferenceEmbedding = array
            array
        } catch (e: Exception) {
            null
        }
    }

    fun clearEmbedding() {
        prefs.edit().remove("cached_embedding").apply()
        SessionManager.currentReferenceEmbedding = null
    }

    fun clear() {
        prefs.edit().clear().apply()
        SessionManager.clear()
    }
}

