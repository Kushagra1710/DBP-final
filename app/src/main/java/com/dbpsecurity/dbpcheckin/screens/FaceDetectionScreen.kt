package com.dbpsecurity.dbpcheckin.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.location.Location
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dbpsecurity.dbpcheckin.models.Group
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Profile
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.tasks.await
import com.dbpsecurity.dbpcheckin.utils.FaceRecognitionHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random
import com.dbpsecurity.dbpcheckin.data.SessionManager
import com.dbpsecurity.dbpcheckin.data.PreferencesManager

@Composable
fun FaceDetectionScreen(navController: NavController, actionType: String = "CHECK_IN", shiftName: String? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val preferencesManager = remember { PreferencesManager(context) }

    var hasPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Initializing...\n(आरंभ हो रहा है...)") }
    var referenceEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var faceRecognitionHelper by remember { mutableStateOf<FaceRecognitionHelper?>(null) }
    var userGroup by remember { mutableStateOf<Group?>(null) }
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    var isMarkingAttendance by remember { mutableStateOf(false) }

    // Verification Steps State
    var isLocationVerified by remember { mutableStateOf(false) }
    var isFaceVerified by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf("Checking location...") }
    var consecutiveMatchCount by remember { mutableStateOf(0) } // New: Counter for consecutive matches

    // Simplified State
    // No liveness challenges anymore


    val scope = rememberCoroutineScope()
    val supabase = SupabaseClient.client
    val fusedLocationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }

    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Needed for head pose
            .build()
        FaceDetection.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            faceRecognitionHelper?.close()
            detector.close()
        }
    }

    // Initialize FaceRecognitionHelper
    LaunchedEffect(Unit) {
        try {
            faceRecognitionHelper = FaceRecognitionHelper(context)
        } catch (e: Throwable) {
            statusMessage = "Error: Model file not found. Please download facenet.tflite"
            Log.e("FaceDetection", "Model init failed", e)
        }
    }

    // Fetch Profile and Generate Reference Embedding
    LaunchedEffect(faceRecognitionHelper) {
        if (faceRecognitionHelper == null) return@LaunchedEffect

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // Try to load from cache first (Memory then Disk)
            if (SessionManager.currentUserProfile == null) {
                userProfile = preferencesManager.getProfile()
            } else {
                userProfile = SessionManager.currentUserProfile
            }

            // Verify if loaded profile matches current user
            if (userProfile != null && userProfile!!.id != userId) {
                userProfile = null // Mismatch, reload
                preferencesManager.clear()
            }

            // CACHE VALIDATION: Background check for updates
            // Fetch critical fields to check for changes since updated_at might be missing
            if (userProfile != null) {
                val cachedProfile = userProfile!!
                scope.launch {
                    try {
                        val serverProfile = withContext(Dispatchers.IO) {
                            supabase.from("profiles").select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id", "group_id", "image_url", "request_status", "name", "tehsil")) {
                                filter { eq("id", userId) }
                            }.decodeSingleOrNull<Profile>()
                        }

                        if (serverProfile != null) {
                            val isStale = serverProfile.groupId != cachedProfile.groupId ||
                                    serverProfile.imageUrl != cachedProfile.imageUrl ||
                                    serverProfile.requestStatus != cachedProfile.requestStatus ||
                                    serverProfile.name != cachedProfile.name ||
                                    serverProfile.seating != cachedProfile.seating

                            if (isStale) {
                                Log.d("FaceDetection", "Cache stale. Reloading profile...")
                                withContext(Dispatchers.Main) {
                                    val freshProfile = withContext(Dispatchers.IO) {
                                        supabase.from("profiles").select {
                                            filter { eq("id", userId) }
                                        }.decodeSingleOrNull<Profile>()
                                    }

                                    if (freshProfile != null) {
                                        userProfile = freshProfile
                                        SessionManager.currentUserProfile = freshProfile
                                        preferencesManager.saveProfile(freshProfile)

                                        if (freshProfile.groupId != null) {
                                             val freshGroup = withContext(Dispatchers.IO) {
                                                supabase.from("groups").select {
                                                    filter { eq("id", freshProfile.groupId) }
                                                }.decodeSingleOrNull<Group>()
                                            }
                                            userGroup = freshGroup
                                            if (freshGroup != null) {
                                                SessionManager.currentUserGroup = freshGroup
                                                preferencesManager.saveGroup(freshGroup)
                                            }
                                        }

                                        if (freshProfile.imageUrl != cachedProfile.imageUrl) {
                                            referenceEmbedding = null
                                            SessionManager.currentReferenceEmbedding = null
                                            // Re-trigger embedding generation if needed
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CacheCheck", "Failed to validate cache", e)
                    }
                }
            }

             if (userProfile != null) {
                 if (SessionManager.currentUserGroup == null) {
                    userGroup = preferencesManager.getGroup()
                 } else {
                    userGroup = SessionManager.currentUserGroup
                 }

                 if (SessionManager.currentReferenceEmbedding == null) {
                    referenceEmbedding = preferencesManager.getEmbedding()
                 } else {
                    referenceEmbedding = SessionManager.currentReferenceEmbedding
                 }

                if (referenceEmbedding != null) {
                    statusMessage = "Ready. Please look at the camera.\n(तैयार। कृपया कैमरे की ओर देखें।)"
                    isLoading = false
                }
            }

            if (userProfile == null) {
                statusMessage = "Fetching profile...\n(प्रोफ़ाइल लाई जा रही है...)"
                try {
                    val profile = withContext(Dispatchers.IO) {
                        supabase.from("profiles").select {
                            filter { eq("id", userId) }
                        }.decodeSingleOrNull<Profile>()
                    }
                    userProfile = profile
                    if (profile != null) {
                        SessionManager.currentUserProfile = profile // Cache for next time
                        preferencesManager.saveProfile(profile)
                    }
                } catch (e: Exception) {
                    statusMessage = "Error fetching profile: ${e.message}"
                    isLoading = false
                }
            }

            if (userProfile != null) {
                val currentProfile = userProfile!!
                try {
                    if (currentProfile.groupId != null) {
                     if (userGroup == null) {
                        try {
                            userGroup = withContext(Dispatchers.IO) {
                                supabase.from("groups").select {
                                    filter { eq("id", currentProfile.groupId) }
                                }.decodeSingleOrNull<Group>()
                            }
                            if (userGroup != null) {
                                SessionManager.currentUserGroup = userGroup // Cache for next time
                                preferencesManager.saveGroup(userGroup!!)
                            }
                        } catch (e: Exception) {
                            Log.e("FaceDetection", "Error fetching group", e)
                        }
                     }
                    } // Closing currentProfile.groupId check

                    // Verify Location Logic
                    if (userGroup != null) {
                        val isIndividualMode = userGroup!!.isLocationRestricted // repurposed field
                        val targetLat: Double
                        val targetLong: Double
                        val targetRadius: Double
                        val locationName: String

                        if (isIndividualMode) {
                            // Individual Mode: Use Profile Location
                            targetLat = currentProfile.latitude ?: 0.0
                            targetLong = currentProfile.longitude ?: 0.0
                            targetRadius = currentProfile.radius ?: 100.0
                            locationName = "assigned location"
                        } else {
                            // Group Mode: Use Group Location
                            targetLat = userGroup!!.latitude
                            targetLong = userGroup!!.longitude
                            targetRadius = userGroup!!.radius
                            locationName = "office"
                        }

                        if (targetLat == 0.0 || targetLong == 0.0) {
                            isLocationVerified = false
                            locationStatus = if (isIndividualMode) "Individual location not set by Admin" else "Department location invalid"
                        } else {
                            val location = try {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    fusedLocationClient.lastLocation.await()
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }

                            if (location != null) {
                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    location.latitude, location.longitude,
                                    targetLat, targetLong,
                                    results
                                )
                                val distanceInMeters = results[0]

                                if (distanceInMeters <= targetRadius) {
                                    isLocationVerified = true
                                    locationStatus = "Location Verified (${distanceInMeters.toInt()}m)"
                                } else {
                                    isLocationVerified = false
                                    locationStatus = "Too far from $locationName (${distanceInMeters.toInt()}m > ${targetRadius.toInt()}m)"
                                }
                            } else {
                                locationStatus = "Could not fetch location"
                            }
                        }
                    }

                // Skip image processing if we already have the embedding
                if (referenceEmbedding != null) {
                    // Already loaded from cache
                } else if (currentProfile.imageUrl != null) {
                    statusMessage = "Loading profile image...\n(प्रोफ़ाइल छवि लोड हो रही है...)"
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(currentProfile.imageUrl)
                        .allowHardware(false) // Essential for pixel access
                        .build()

                    val result = withContext(Dispatchers.IO) { loader.execute(request) }
                    if (result is SuccessResult) {
                        val drawable = result.drawable
                        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap

                        if (bitmap != null) {
                            statusMessage = "Generating reference embedding..."

                            // Detect face in profile image to crop it
                            val inputImage = InputImage.fromBitmap(bitmap, 0)
                            // Use a separate detector for high accuracy on profile image
                            val profileDetector = FaceDetection.getClient(
                                FaceDetectorOptions.Builder()
                                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                    .build()
                            )

                            profileDetector.process(inputImage)
                                .addOnSuccessListener { faces ->
                                    if (faces.isNotEmpty()) {
                                        val face = faces[0]
                                        val croppedBitmap = cropBitmap(bitmap, face.boundingBox)
                                        if (croppedBitmap != null) {
                                            // Move to background thread for embedding generation
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val embedding = faceRecognitionHelper?.getFaceEmbedding(croppedBitmap)
                                                    withContext(Dispatchers.Main) {
                                                        referenceEmbedding = embedding
                                                        if (embedding != null) {
                                                            SessionManager.currentReferenceEmbedding = embedding // Cache it
                                                            preferencesManager.saveEmbedding(embedding)
                                                        }
                                                        statusMessage = "Ready. Please look at the camera.\n(तैयार। कृपया कैमरे की ओर देखें।)"
                                                        isLoading = false
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        statusMessage = "Error generating embedding: ${e.message}"
                                                        isLoading = false
                                                    }
                                                }
                                            }
                                        } else {
                                            statusMessage = "Could not crop face from profile."
                                            isLoading = false
                                        }
                                    } else {
                                        statusMessage = "No face found in profile picture."
                                        isLoading = false
                                    }
                                }
                                .addOnFailureListener { e ->
                                    statusMessage = "Failed to process profile image: ${e.message}"
                                    isLoading = false
                                }
                                .addOnCompleteListener {
                                    profileDetector.close()
                                }
                        } else {
                            statusMessage = "Profile image is not a valid bitmap."
                            isLoading = false
                        }
                    } else {
                        statusMessage = "Failed to load profile image."
                        isLoading = false
                    }
                } else {
                        statusMessage = "Profile image not found."
                        isLoading = false
                    }
                } catch (e: Exception) {
                    statusMessage = "Error: ${e.message}"
                    isLoading = false
                    e.printStackTrace()
                }
            }
        } else {
            statusMessage = "User not logged in."
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.CAMERA] == true &&
                (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    }

    LaunchedEffect(Unit) {
        val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val locationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

        if (cameraPermission == PackageManager.PERMISSION_GRANTED && locationPermission == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (hasPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(statusMessage, modifier = Modifier.padding(top = 16.dp))
                }
            } else {
                // Camera Preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    var lastAnalyzedTimestamp = 0L
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        val currentTimestamp = System.currentTimeMillis()
                                        if (currentTimestamp - lastAnalyzedTimestamp >= 500) { // Throttle: 500ms
                                            lastAnalyzedTimestamp = currentTimestamp
                                            try {
                                                // Only process face if not already verified
                                                if (!isFaceVerified && !isMarkingAttendance) {
                                                    processImageProxy(
                                                        imageProxy,
                                                        faceRecognitionHelper,
                                                        referenceEmbedding,
                                                        detector,
                                                        scope,
                                                        onMatchFound = {
                                                            consecutiveMatchCount++
                                                            if (consecutiveMatchCount >= 3) {
                                                                isFaceVerified = true
                                                                statusMessage = "Face Verified!\n(चेहरा सत्यापित!)"
                                                            } else {
                                                                statusMessage = "Verifying... ($consecutiveMatchCount/3)\n(सत्यापन हो रहा है...)"
                                                            }
                                                        },
                                                        onMatchFailed = {
                                                            consecutiveMatchCount = 0
                                                            statusMessage = "Not Recognized\n(पहचाना नहीं गया)"
                                                        },
                                                        onStatusUpdate = { msg ->
                                                            statusMessage = msg
                                                        }
                                                    )
                                                } else {
                                                    imageProxy.close()
                                                }
                                            } catch (e: Throwable) {
                                                Log.e("FaceDetection", "Analyzer fatal error", e)
                                                imageProxy.close()
                                            }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                Log.e("FaceDetection", "Use case binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Flash Overlay - Removed as we are using gestures now
                // if (currentChallenge == LivenessChallenge.COLOR_FLASH && !isChallengeCompleted) { ... }

                // Overlay UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Status Cards
                    Column {
                        // Location Status
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLocationVerified) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = locationStatus,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Face Status
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isFaceVerified) Color(0xFF4CAF50) else Color(0xFF2196F3)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isFaceVerified) "Face Verified" else statusMessage,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Bottom Action Button
                    if (isLocationVerified && isFaceVerified) {
                        Button(
                            onClick = {
                                if (!isMarkingAttendance) {
                                    isMarkingAttendance = true
                                    scope.launch {
                                        submitAttendance(context, navController, supabase, fusedLocationClient, userGroup, userProfile, actionType, shiftName)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isMarkingAttendance) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(end = 8.dp))
                                Text("Marking...", style = MaterialTheme.typography.titleMedium)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mark Attendance", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required for attendance.")
        }
    }
}



@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
fun processImageProxy(
    imageProxy: ImageProxy,
    helper: FaceRecognitionHelper?,
    referenceEmbedding: FloatArray?,
    detector: com.google.mlkit.vision.face.FaceDetector,
    scope: kotlinx.coroutines.CoroutineScope,
    onMatchFound: () -> Unit,
    onMatchFailed: (String) -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && helper != null && referenceEmbedding != null) {
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    // 1. Multiple Faces Check
                    if (faces.size > 1) {
                        scope.launch(Dispatchers.Main) {
                            onStatusUpdate("Multiple faces detected. Only one person allowed.\n(एक से अधिक चेहरे मिले। केवल एक व्यक्ति की अनुमति है।)")
                        }
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    val face = faces[0]

                    // 2. Face Size Check (Basic Quality Check)
                    // We require the face width to be at least 20% of the image width.
                    val faceWidthRatio = face.boundingBox.width().toFloat() / mediaImage.width.toFloat()
                    if (faceWidthRatio < 0.2f) {
                        scope.launch(Dispatchers.Main) {
                            onStatusUpdate("Move Closer (Face too small)\n(कृपया और पास आएं)")
                        }
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    // Directly proceed to Face Recognition (No Liveness Check)
                    scope.launch(Dispatchers.Default) {
                        try {
                            // Convert ImageProxy to Bitmap (Heavy)
                            val bitmap = imageProxyToBitmap(imageProxy)
                            if (bitmap != null) {
                                val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
                                val croppedBitmap = cropBitmap(rotatedBitmap, face.boundingBox)

                                if (croppedBitmap != null) {
                                    // Generate Embedding (Heavy)
                                    val currentEmbedding = helper.getFaceEmbedding(croppedBitmap)
                                    val distance = helper.calculateDistance(currentEmbedding, referenceEmbedding)

                                    Log.d("FaceRecognition", "Distance: $distance")

                                    withContext(Dispatchers.Main) {
                                        // Stricter threshold (0.85f) and consecutive match requirement
                                        if (distance < 0.85f) {
                                            onMatchFound() // This increments the counter in the UI logic
                                        } else {
                                            onMatchFailed("Not Recognized (Dist: ${String.format(Locale.US, "%.2f", distance)})\n(पहचाना नहीं गया)")
                                        }
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            Log.e("FaceDetection", "Error processing face", e)
                            withContext(Dispatchers.Main) {
                                onStatusUpdate("Error: ${e.message}")
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }

                } else {
                    scope.launch(Dispatchers.Main) {
                        onStatusUpdate("No face detected\n(चेहरा नहीं मिला)")
                    }
                    imageProxy.close()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Face detection failed", e)
                onStatusUpdate("Detection failed: ${e.message}")
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    try {
        if (image.planes.isEmpty()) return null
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        Log.e("FaceDetection", "Error converting ImageProxy to Bitmap", e)
        return null
    }
}

fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap? {
    // Expand the rectangle by 20%
    val marginX = (rect.width() * 0.2f).toInt()
    val marginY = (rect.height() * 0.2f).toInt()

    val left = (rect.left - marginX).coerceAtLeast(0)
    val top = (rect.top - marginY).coerceAtLeast(0)
    var width = (rect.width() + 2 * marginX)
    var height = (rect.height() + 2 * marginY)

    if (left + width > bitmap.width) width = bitmap.width - left
    if (top + height > bitmap.height) height = bitmap.height - top

    return if (width > 0 && height > 0) {
        Bitmap.createBitmap(bitmap, left, top, width, height)
    } else {
        null
    }
}


suspend fun submitAttendance(
    context: Context,
    navController: NavController,
    supabase: io.github.jan.supabase.SupabaseClient,
    locationClient: FusedLocationProviderClient,
    userGroup: Group?,
    userProfile: Profile?,
    actionType: String = "CHECK_IN",
    shiftName: String? = null
) {
    try {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Fetch location
        val location = try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationClient.lastLocation.await()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        if (location == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: Could not fetch current location.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val locationString = "${location.latitude},${location.longitude}"
        // Placeholder for now, can be updated to upload actual image if needed
        val imageUrl = "verified_by_face_recognition" 

        // Constructing correct params object
        val currentUserId = SessionManager.currentUserProfile?.id ?: userId
        if (currentUserId.isEmpty()) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Error: User ID missing. Please restart.", Toast.LENGTH_LONG).show() }
            return
        }

        val rpcParams = if (actionType == "CHECK_IN") {
             mapOf(
                 "p_user_id" to currentUserId,
                 "p_image_url" to imageUrl, 
                 "p_location" to locationString,
                 "p_shift_name" to shiftName
             )
        } else {
             // Check Out
             mapOf(
                 "p_user_id" to currentUserId,
                 "p_image_url" to imageUrl, 
                 "p_location" to locationString
             )
        }

        val rpcName = if (actionType == "CHECK_IN") "check_in_user" else "check_out_user"
        supabase.postgrest.rpc(rpcName, rpcParams)

        withContext(Dispatchers.Main) {
            val successMsg = if (actionType == "CHECK_IN") "Check-In Successful!" else "Check-Out Successful!"
            Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
            navController.popBackStack()
        }
    } catch (e: Exception) {
        val errorMessage = e.message ?: ""
        Log.e("Attendance", "Error marking attendance", e)
        withContext(Dispatchers.Main) {
             // Show friendly error for RPC exceptions (e.g. "Too early", "Window closed")
             if (errorMessage.contains("Too early")) {
                 Toast.makeText(context, "Too early to check out. Minimum 6 hours required.", Toast.LENGTH_LONG).show()
             } else if (errorMessage.contains("Window closed")) {
                 Toast.makeText(context, "Checkout window closed or invalid time.", Toast.LENGTH_LONG).show()
             } else if (errorMessage.contains("Check-in window")) {
                 Toast.makeText(context, errorMessage.substringAfter("exception:").substringBefore("\n"), Toast.LENGTH_LONG).show()
             } else {
                 Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_LONG).show()
             }
        }
    }
}

@Serializable
data class AttendanceEntry(
    @SerialName("user_id") val userId: String,
    val status: String,
    @SerialName("image_url") val imageUrl: String,
    val latitude: Double,
    val longitude: Double,
    val name: String?,
    val tehsil: String? // Keep as tehsil for DB compatibility
)


