package com.dbpsecurity.dbpcheckin.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object AttendanceCleanupManager {
    private const val PREFS_NAME = "cleanup_prefs"
    private const val KEY_LAST_CLEANUP = "last_cleanup_timestamp"
    private const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    fun checkAndPerformCleanup(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCleanupTime = prefs.getLong(KEY_LAST_CLEANUP, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            Log.d("AttendanceCleanup", "Starting cleanup check...")
            performCleanup(prefs, currentTime)
        } else {
            Log.d("AttendanceCleanup", "Cleanup skipped. Last run was recently.")
        }
    }

    private fun performCleanup(prefs: SharedPreferences, currentTime: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // calculate date 3 months ago
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.add(Calendar.MONTH, -3)
                
                // Format: 2023-10-27T10:00:00 (Supabase/Postgres format)
                // We use UTC to match server time broadly
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val thresholdDate = sdf.format(calendar.time)

                Log.d("AttendanceCleanup", "Deleting records created before: $thresholdDate")

                val supabase = SupabaseClient.client
                
                // Delete rows where created_at < thresholdDate
                supabase.from("attendance").delete {
                    filter {
                        lt("created_at", thresholdDate)
                    }
                }

                // Update last run time on success
                prefs.edit().putLong(KEY_LAST_CLEANUP, currentTime).apply()
                Log.d("AttendanceCleanup", "Cleanup successful. Records older than 3 months removed.")

            } catch (e: Exception) {
                Log.e("AttendanceCleanup", "Error performing cleanup: ${e.message}", e)
            }
        }
    }
}
