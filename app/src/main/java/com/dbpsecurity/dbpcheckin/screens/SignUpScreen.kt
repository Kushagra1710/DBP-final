package com.dbpsecurity.dbpcheckin.screens

import android.net.Uri
import android.widget.Toast
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.ui.theme.Primary
import com.dbpsecurity.dbpcheckin.ui.theme.Secondary
import com.dbpsecurity.dbpcheckin.ui.theme.White
import com.dbpsecurity.dbpcheckin.ui.theme.Accent
import com.dbpsecurity.dbpcheckin.ui.theme.Black
import com.dbpsecurity.dbpcheckin.ui.theme.Error
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedSeating by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val seatings = listOf("Sanchi", "Gairatganj", "Begamganj", "Silwani", "Udaipura", "Badi/bareli", "Obedullaganj", "Raisen", "Sultanganj")

    // Department Selection State
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var groupExpanded by remember { mutableStateOf(false) }
    var isGroupsLoading by remember { mutableStateOf(true) }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }

    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()
    val fixedLightScheme = lightColorScheme(
        primary = Primary,
        secondary = Secondary,
        tertiary = Accent,
        background = Color(0xFFFAFAFA),
        surface = White,
        onPrimary = White,
        onSecondary = Black,
        onTertiary = Black,
        error = Error,
        onError = White,
        errorContainer = Color(0xFFFFDADA),
        onErrorContainer = Error
    )
    val signUpFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = fixedLightScheme.primary,
        unfocusedBorderColor = fixedLightScheme.outline,
        focusedLabelColor = fixedLightScheme.primary,
        unfocusedLabelColor = fixedLightScheme.onSurfaceVariant,
        cursorColor = fixedLightScheme.primary,
        focusedTextColor = fixedLightScheme.onSurface,
        unfocusedTextColor = fixedLightScheme.onSurface,
        focusedLeadingIconColor = fixedLightScheme.primary,
        unfocusedLeadingIconColor = fixedLightScheme.onSurfaceVariant,
        focusedTrailingIconColor = fixedLightScheme.primary,
        unfocusedTrailingIconColor = fixedLightScheme.onSurfaceVariant,
        disabledBorderColor = fixedLightScheme.outline,
        disabledLabelColor = fixedLightScheme.onSurfaceVariant,
        disabledTextColor = fixedLightScheme.onSurface,
        disabledLeadingIconColor = fixedLightScheme.onSurfaceVariant,
        disabledTrailingIconColor = fixedLightScheme.onSurfaceVariant
    )

    // Fetch Groups
    LaunchedEffect(Unit) {
        try {
            val fetchedGroups = supabase.from("groups").select().decodeList<Group>()
            groups = fetchedGroups.sortedBy { it.name.lowercase(Locale.getDefault()) }
        } catch (e: Exception) {
            Log.e("SignUpScreen", "Error fetching groups", e)
        } finally {
            isGroupsLoading = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) {
            imageUri = null
            photoFile = null
        }
    }

    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            photoFile = this
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val file = createImageFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                imageUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error launching camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Primary, Secondary),
                    startY = 0f,
                    endY = 2000f
                )
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineLarge,
            color = White,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Join our attendance system",
            style = MaterialTheme.typography.bodyMedium,
            color = White.copy(alpha = 0.9f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.size(140.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        permissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUri),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountBox,
                            contentDescription = "Add Photo",
                            tint = Primary,
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap to take photo",
                            color = Primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = signUpFieldColors,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address *") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = signUpFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) phone = it },
                    label = { Text("Phone Number *") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = signUpFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password *") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else
                            Icons.Filled.VisibilityOff

                        val description = if (passwordVisible) "Hide password" else "Show password"

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = signUpFieldColors,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Seating Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedSeating,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Seating *") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Seating") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = signUpFieldColors
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(White) // ensure dropdown is visible
                    ) {
                        seatings.forEach { seating ->
                            DropdownMenuItem(
                                text = { Text(text = seating, color = fixedLightScheme.onSurface) },
                                onClick = {
                                    selectedSeating = seating
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Department Selection Dropdown
                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = {
                        if (!isGroupsLoading && groups.isNotEmpty()) {
                            groupExpanded = !groupExpanded
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when {
                            isGroupsLoading -> "Loading departments..."
                            groups.isEmpty() -> "No departments available"
                            else -> selectedGroup?.name ?: ""
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Department *") },
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = "Department") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = signUpFieldColors,
                        enabled = !isGroupsLoading && groups.isNotEmpty()
                    )
                    ExposedDropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false },
                        modifier = Modifier.background(White)
                    ) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(text = group.name, color = fixedLightScheme.onSurface) },
                                onClick = {
                                    selectedGroup = group
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (name.isNotEmpty() && email.isNotBlank() && password.isNotEmpty() && phone.length == 10 && selectedSeating.isNotEmpty() && photoFile != null && selectedGroup != null) {
                    isLoading = true
                    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { authResult ->
                            val userId = authResult.user?.uid
                            if (userId != null) {
                                scope.launch {
                                    try {
                                        val photoBytes = photoFile!!.readBytes()
                                        val storagePath = "public/$userId/${photoFile!!.name}"
                                        supabase.storage.from("profile-pictures").upload(storagePath, photoBytes, upsert = true)
                                        val imageUrl = supabase.storage.from("profile-pictures").publicUrl(storagePath)

                                        // Use Map to ensure explicit null handling for group_id
                                        val profileData = mapOf(
                                            "id" to userId,
                                            "name" to name,
                                            "email" to email.trim(),
                                            "phone" to phone,
                                            "tehsil" to selectedSeating, // Map seating to tehsil column
                                            "image_url" to imageUrl,
                                            "role" to "employee",
                                            "group_id" to null,
                                            "requested_group_id" to selectedGroup?.id,
                                            "request_status" to "pending"
                                        )
                                        supabase.from("profiles").insert(profileData)

                                        isLoading = false
                                        Toast.makeText(context, "Sign Up Successful! Please wait for admin approval.", Toast.LENGTH_LONG).show()
                                        navController.navigate("signin") {
                                            popUpTo("signup") { inclusive = true }
                                        }

                                    } catch (e: Exception) {
                                        isLoading = false
                                        Log.e("SignUpScreen", "Profile Creation Failed", e)
                                        Toast.makeText(context, "Profile Creation Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            Toast.makeText(context, "Sign Up Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(context, "Please fill all fields (including Department) and take a photo", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = White),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = White, strokeWidth = 2.dp)
            } else {
                Text("Create Account", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { navController.navigate("signin") }) {
            Text("Already have an account? Sign In", color = White, fontWeight = FontWeight.SemiBold)
        }
    }
}

