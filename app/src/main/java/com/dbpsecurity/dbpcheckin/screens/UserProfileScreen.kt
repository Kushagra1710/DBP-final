package com.dbpsecurity.dbpcheckin.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Group // Add this import
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Group // Add this import
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    var userProfile by remember { mutableStateOf<Profile?>(null) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var seating by remember { mutableStateOf("") }
    var departmentName by remember { mutableStateOf("Loading...") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                try {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val photoBytes = inputStream?.readBytes()

                    if (photoBytes != null) {
                        val fileName = "profile_${System.currentTimeMillis()}.jpg"
                        val storagePath = "public/$userId/$fileName"

                        // Upload
                        supabase.storage.from("profile-pictures").upload(storagePath, photoBytes, upsert = true)
                        val imageUrl = supabase.storage.from("profile-pictures").publicUrl(storagePath)

                        // Update Profile and Verify
                        val updatedList = supabase.from("profiles").update({
                            "image_url" to imageUrl
                        }) {
                            filter { eq("id", userId) }
                            select()
                        }.decodeList<Profile>()

                        if (updatedList.isNotEmpty()) {
                            // Refresh local state with the confirmed server data
                            userProfile = updatedList[0]
                            
                            // FIX: Invalidate cached embedding so it regenerates on next scan
                            // Also ensure the GLOBAL Preference cache has the new profile (with new URL)
                            com.dbpsecurity.dbpcheckin.data.PreferencesManager(context).apply {
                                saveEmbedding(FloatArray(0)) // Clearing embedding effectively
                                // Or better: we will add a clearEmbedding method in next step, 
                                // for now let's just use the existing mechanism to save profile which is critical
                                saveProfile(userProfile!!)
                                
                                // Manually clear the embedding preference
                                context.getSharedPreferences("dbp_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit().remove("cached_embedding").apply()
                                com.dbpsecurity.dbpcheckin.data.SessionManager.currentReferenceEmbedding = null
                            }
                            
                            Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Fallback to RPC if standard update fails (likely RLS)
                            try {
                                val params = mapOf("p_user_id" to userId, "p_image_url" to imageUrl)
                                supabase.postgrest.rpc("update_profile_image_by_id", params)
                                userProfile = userProfile?.copy(imageUrl = imageUrl)
                                // FIX: Invalidate cached embedding so it regenerates on next scan
                                com.dbpsecurity.dbpcheckin.data.PreferencesManager(context).apply {
                                    clearEmbedding() // We'll add this method or use key deletion
                                    saveProfile(userProfile!!) // Sync new profile (with new URL) to global cache
                                }
                                Toast.makeText(context, "Profile picture updated (RPC)!", Toast.LENGTH_SHORT).show()
                            } catch (rpcError: Exception) {
                                throw Exception("Database update failed. Check permissions.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error updating photo: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "You are not logged in.", Toast.LENGTH_LONG).show()
            navController.navigate("signin") { popUpTo(0) }
            return@LaunchedEffect
        }
        try {
            val profile = supabase.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingle<Profile>()
            userProfile = profile
            name = profile.name ?: ""
            email = profile.email ?: ""
            phone = profile.phone ?: ""
            seating = profile.seating ?: ""

            // Fetch Department Name
            if (profile.groupId != null) {
                val group = supabase.from("groups").select {
                    filter { eq("id", profile.groupId) }
                }.decodeSingleOrNull<Group>()
                departmentName = group?.name ?: "Unknown Department"
            } else if (profile.requestedGroupId != null) {
                val group = supabase.from("groups").select {
                    filter { eq("id", profile.requestedGroupId) }
                }.decodeSingleOrNull<Group>()
                departmentName = "${group?.name ?: "Unknown"} (Requested)"
            } else {
                departmentName = "Not Assigned"
            }

            isLoading = false
        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "Failed to load profile: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Primary, Secondary),
                    startY = 0f,
                    endY = 2000f
                )
            )
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = White)
        } else if (userProfile != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Box(contentAlignment = Alignment.BottomEnd) {
                    Card(
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = userProfile!!.imageUrl),
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Photo", tint = White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = userProfile!!.email ?: "No Email",
                    style = MaterialTheme.typography.titleLarge,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = DarkGrey,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = DarkGrey,
                                focusedTextColor = Black,
                                unfocusedTextColor = Black,
                                cursorColor = Primary,
                                focusedLeadingIconColor = Primary,
                                unfocusedLeadingIconColor = DarkGrey
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = {},
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = DarkGrey,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = DarkGrey,
                                focusedTextColor = Black,
                                unfocusedTextColor = Black,
                                cursorColor = Primary,
                                focusedLeadingIconColor = Primary,
                                unfocusedLeadingIconColor = DarkGrey,
                                disabledBorderColor = DarkGrey,
                                disabledLabelColor = DarkGrey,
                                disabledTextColor = Black,
                                disabledLeadingIconColor = DarkGrey
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = DarkGrey,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = DarkGrey,
                                focusedTextColor = Black,
                                unfocusedTextColor = Black,
                                cursorColor = Primary,
                                focusedLeadingIconColor = Primary,
                                unfocusedLeadingIconColor = DarkGrey
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = seating,
                            onValueChange = {},
                            label = { Text("Seating") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = DarkGrey,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = DarkGrey,
                                focusedTextColor = Black,
                                unfocusedTextColor = Black,
                                cursorColor = Primary,
                                focusedLeadingIconColor = Primary,
                                unfocusedLeadingIconColor = DarkGrey,
                                disabledBorderColor = DarkGrey,
                                disabledLabelColor = DarkGrey,
                                disabledTextColor = Black,
                                disabledLeadingIconColor = DarkGrey
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = departmentName,
                            onValueChange = {},
                            label = { Text("Department") },
                            leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = DarkGrey,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = DarkGrey,
                                focusedTextColor = Black,
                                unfocusedTextColor = Black,
                                cursorColor = Primary,
                                focusedLeadingIconColor = Primary,
                                unfocusedLeadingIconColor = DarkGrey,
                                disabledBorderColor = DarkGrey,
                                disabledLabelColor = DarkGrey,
                                disabledTextColor = Black,
                                disabledLeadingIconColor = DarkGrey
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        supabase.from("profiles").update({
                                            "name" to name
                                            "phone" to phone
                                        }) {
                                            filter { eq("id", userProfile!!.id) }
                                        }
                                        Toast.makeText(context, "Profile Updated", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Update Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = White
                            )
                        ) {
                            Text("Save Changes")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = DarkGrey,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = DarkGrey,
                                focusedTextColor = Black,
                                unfocusedTextColor = Black,
                                cursorColor = Primary,
                                focusedLeadingIconColor = Primary,
                                unfocusedLeadingIconColor = DarkGrey
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm New Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = DarkGrey,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = DarkGrey,
                                focusedTextColor = Black,
                                unfocusedTextColor = Black,
                                cursorColor = Primary,
                                focusedLeadingIconColor = Primary,
                                unfocusedLeadingIconColor = DarkGrey
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (newPassword.isNotEmpty() && newPassword == confirmPassword) {
                                    scope.launch {
                                        try {
                                            FirebaseAuth.getInstance().currentUser?.updatePassword(newPassword)
                                                ?.addOnSuccessListener {
                                                    Toast.makeText(context, "Password Updated Successfully", Toast.LENGTH_SHORT).show()
                                                    newPassword = ""
                                                    confirmPassword = ""
                                                }
                                                ?.addOnFailureListener { e ->
                                                    Toast.makeText(context, "Password Update Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Password Update Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Passwords do not match or are empty.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = White
                            )
                        ) {
                            Text("Update Password")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                TextButton(onClick = { showSignOutDialog = true }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", color = White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out", fontWeight = FontWeight.Bold, color = Primary) },
            text = { Text("Are you sure you want to sign out?", color = Black) },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        scope.launch {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate("signin") {
                                popUpTo(0)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Error, contentColor = White)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = DarkGrey)
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
