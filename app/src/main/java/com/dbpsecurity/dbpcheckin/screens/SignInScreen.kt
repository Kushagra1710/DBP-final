package com.dbpsecurity.dbpcheckin.screens

import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.dbpsecurity.dbpcheckin.R
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.Primary
import com.dbpsecurity.dbpcheckin.ui.theme.Secondary
import com.dbpsecurity.dbpcheckin.ui.theme.White
import com.dbpsecurity.dbpcheckin.ui.theme.Accent
import com.dbpsecurity.dbpcheckin.ui.theme.Black
import com.dbpsecurity.dbpcheckin.ui.theme.DarkGrey
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Primary, Secondary),
                    startY = 0f,
                    endY = 1500f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo/Icon
            Surface(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape),
                color = White,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(R.drawable.security_guard)
                                .decoderFactory(if (SDK_INT >= 28) ImageDecoderDecoder.Factory() else GifDecoder.Factory())
                                .build()
                        ),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(120.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "DBP Check-In",
                style = MaterialTheme.typography.headlineLarge,
                color = White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Attendance Management System",
                style = MaterialTheme.typography.bodyMedium,
                color = White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Login Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "Email", tint = Primary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            focusedLabelColor = Primary,
                            cursorColor = Primary,
                            focusedTextColor = Black,
                            unfocusedTextColor = Black,
                            unfocusedLabelColor = DarkGrey,
                            unfocusedBorderColor = DarkGrey
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "Password", tint = Primary)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            focusedLabelColor = Primary,
                            cursorColor = Primary,
                            focusedTextColor = Black,
                            unfocusedTextColor = Black,
                            unfocusedLabelColor = DarkGrey,
                            unfocusedBorderColor = DarkGrey
                        ),
                        singleLine = true
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TextButton(onClick = { showForgotPasswordDialog = true }) {
                            Text(
                                text = "Forgot Password?",
                                color = Primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (email.isNotBlank() && password.isNotEmpty()) {
                                isLoading = true
                                FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
                                    .addOnSuccessListener { authResult ->
                                        val userId = authResult.user?.uid
                                        if (userId != null) {
                                            scope.launch {
                                                try {
                                                    Log.d("SignInScreen", "Fetching profile for userId: $userId")
                                                    val profile = supabase.from("profiles").select {
                                                        filter {
                                                            eq("id", userId)
                                                        }
                                                    }.decodeSingleOrNull<Profile>()

                                                    Log.d("SignInScreen", "Profile fetched: $profile")

                                                    isLoading = false
                                                    if (profile != null) {
                                                        val destination = when (profile.role) {
                                                            "admin" -> "admin_home"
                                                            "block_coordinator" -> "block_coordinator"
                                                            else -> "employee_home"
                                                        }
                                                        navController.navigate(destination) {
                                                            popUpTo(0) { inclusive = true }
                                                        }
                                                    } else {
                                                        Log.e("SignInScreen", "Profile is null for userId: $userId")
                                                        Toast.makeText(context, "Profile not found. Please contact admin or sign up again.", Toast.LENGTH_LONG).show()
                                                    }
                                                } catch (e: Exception) {
                                                    isLoading = false
                                                    Log.e("SignInScreen", "Error fetching profile", e)
                                                    e.printStackTrace()
                                                    Toast.makeText(context, "Error fetching profile: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            isLoading = false
                                            Toast.makeText(context, "Could not retrieve user.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        isLoading = false
                                        Toast.makeText(context, "Login Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            } else {
                                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Sign In",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { navController.navigate("signup") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Don't have an account? Sign Up",
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text("Enter your email address to receive a password reset link.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (email.isNotEmpty()) {
                            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Reset email sent to $email", Toast.LENGTH_LONG).show()
                                    showForgotPasswordDialog = false
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        } else {
                            Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
