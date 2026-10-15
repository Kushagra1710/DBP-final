package com.dbpsecurity.dbpcheckin.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object EmbeddingCacheManager {
    private const val PREF_NAME = "embedding_cache"
    private const val KEY_EMBEDDINGS = "cached_embeddings"
    private var cachedEmbeddings: MutableMap<String, FloatArray> = mutableMapOf()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_EMBEDDINGS, null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<String, FloatArray>>() {}.type
            cachedEmbeddings = Gson().fromJson(json, type)
        }
    }

    fun getEmbedding(userId: String): FloatArray? {
        return cachedEmbeddings[userId]
    }

    fun saveEmbedding(context: Context, userId: String, embedding: FloatArray) {
        cachedEmbeddings[userId] = embedding
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EMBEDDINGS, Gson().toJson(cachedEmbeddings)).apply()
    }

    fun clear(context: Context) {
        cachedEmbeddings.clear()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
