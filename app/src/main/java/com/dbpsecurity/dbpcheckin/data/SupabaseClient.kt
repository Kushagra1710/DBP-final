package com.dbpsecurity.dbpcheckin.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseClient {

    private const val SUPABASE_URL = "https://ecfdcwimlkxalebqxxvk.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVjZmRjd2ltbGt4YWxlYnF4eHZrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjYzMjUwNDYsImV4cCI6MjA4MTkwMTA0Nn0.DZVpgR6fjMdEFsXYxz-3PvEuAYNzNmBkj1DJXQEtcu4"

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}
