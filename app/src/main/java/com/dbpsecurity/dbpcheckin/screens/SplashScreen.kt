package com.dbpsecurity.dbpcheckin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.PreferencesManager
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.Primary
import com.dbpsecurity.dbpcheckin.ui.theme.Secondary
import com.dbpsecurity.dbpcheckin.ui.theme.White
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        navController.navigate("signin") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                } else {
                    val preferencesManager = PreferencesManager(context)
                    var profile: Profile? = null

                    // Pre-warm TFLite (Thin Client) to avoid lag on Face Detection screen
                    com.dbpsecurity.dbpcheckin.utils.FaceRecognitionHelper.preWarm(context)

                    // 1. Try to fetch fresh profile from server
                    try {
                        profile = SupabaseClient.client.from("profiles").select {
                            filter { eq("id", currentUser.uid) }
                        }.decodeSingleOrNull<Profile>()

                        // If successful, update local cache
                        if (profile != null) {
                            preferencesManager.saveProfile(profile)
                        }
                    } catch (e: Exception) {
                        // Network error or server down - suppress and fall back to cache
                        e.printStackTrace()
                    }

                    // 1.5. CHECK FOR LOGIC SYNC: If Server Photo != Local Photo, Invalidate Embedding
                    // This handles the "Restart Bug" where we download new profile but keep old embedding.
                    val localProfile = preferencesManager.getProfile()
                    if (profile != null && localProfile != null) {
                        if (profile.imageUrl != localProfile.imageUrl) {
                            // Photo changed! The old embedding is now invalid.
                            preferencesManager.clearEmbedding()
                        }
                    }

                    // 2. Fallback to local cache if server failed
                    if (profile == null) {
                        profile = preferencesManager.getProfile()
                    }

                    withContext(Dispatchers.Main) {
                        if (profile != null) {
                            val destination = when (profile.role) {
                                "admin" -> "admin_home"
                                "block_coordinator" -> "block_coordinator"
                                else -> "employee_home"
                            }
                            navController.navigate(destination) {
                                popUpTo("splash") { inclusive = true }
                            }
                        } else {
                            // 3. Only sign out if BOTH server and cache failed
                            Toast.makeText(context, "Session expired or profile missing", Toast.LENGTH_SHORT).show()
                            FirebaseAuth.getInstance().signOut() // Clean up session
                            navController.navigate("signin") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Unexpected fatal error
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("signin") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Primary, Secondary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = White)
    }
}
