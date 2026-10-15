package com.dbpsecurity.dbpcheckin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Attendance
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun EmployeeHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    var userProfile by remember { mutableStateOf<Profile?>(null) }
    var groupName by remember { mutableStateOf("Loading...") }
    var isAttendanceWindowOpen by remember { mutableStateOf(false) }
    var isAttendanceMarked by remember { mutableStateOf(false) }
    var attendanceTime by remember { mutableStateOf<String?>(null) }

    var showReApplyDialog by remember { mutableStateOf(false) }
    var availableGroups by remember { mutableStateOf<List<Group>>(emptyList()) }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun fetchData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "You are not logged in.", Toast.LENGTH_LONG).show()
            navController.navigate("signin") {
                popUpTo(0
                )
            }
            return
        }

        scope.launch {
            try {
                val profile = supabase.from("profiles").select {
                    filter { eq("id", userId) }
                }.decodeSingle<Profile>()
                userProfile = profile

                // Fetch all groups for re-apply dialog if needed
                if (profile.requestStatus == "rejected") {
                     val groups = supabase.from("groups").select().decodeList<Group>()
                     availableGroups = groups
                }

                if (profile.groupId != null && profile.groupId.isNotBlank()) {
                    val group = supabase.from("groups").select {
                        filter { eq("id", profile.groupId) }
                    }.decodeSingleOrNull<Group>()
                    groupName = group?.name ?: "Unknown Department"

                    if (group != null) {
                        try {
                            val now = Calendar.getInstance()
                            val currentTime = now.time

                            fun parseTime(timeStr: String): Date? {
                                val formats = listOf("HH:mm:ss", "HH:mm")
                                for (format in formats) {
                                    try {
                                        val sdf = SimpleDateFormat(format, Locale.getDefault())
                                        val date = sdf.parse(timeStr)
                                        if (date != null) {
                                            val cal = Calendar.getInstance()
                                            cal.time = date

                                            val todayCal = Calendar.getInstance()
                                            todayCal.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                                            todayCal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
                                            todayCal.set(Calendar.SECOND, cal.get(Calendar.SECOND))
                                            todayCal.set(Calendar.MILLISECOND, 0)
                                            return todayCal.time
                                        }
                        } catch (_: Exception) {
                            // Try next format
                        }
                                }
                                return null
                            }

                            val start = parseTime(group.startTime)
                            val end = parseTime(group.endTime)

                            if (start != null && end != null) {
                                isAttendanceWindowOpen = !currentTime.before(start) && !currentTime.after(end)
                            } else {
                                isAttendanceWindowOpen = false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isAttendanceWindowOpen = false
                        }
                    }
                } else {
                    // Check request status
                    if (profile.requestStatus == "pending") {
                        groupName = "Request Pending"
                    } else if (profile.requestStatus == "rejected") {
                        groupName = "Request Rejected"
                    } else {
                        groupName = "Not Assigned to a Department"
                    }
                }
            } catch (_: Exception) {
                groupName = "Error fetching data"
                // Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }

            // Check for existing attendance
            try {
                val attendanceList = supabase.from("attendance").select {
                    filter { eq("user_id", userId) }
                    order("created_at", order = Order.DESCENDING)
                    limit(1)
                }.decodeList<Attendance>()

                if (attendanceList.isNotEmpty()) {
                    val latest = attendanceList[0]
                    val createdAt = latest.createdAt
                    if (createdAt != null) {
                        try {
                            // Parse ISO 8601 string
                            // Supabase format example: 2023-10-27T10:00:00.123456+00:00
                            // We'll try to handle it by cleaning up fractional seconds if needed or using a flexible parser
                            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                            isoFormat.timeZone = TimeZone.getTimeZone("UTC") // Supabase returns UTC usually

                            // Simple hack to handle variable fractional seconds: take the first 19 chars
                            val cleanDateStr = if (createdAt.length >= 19) createdAt.substring(0, 19) else createdAt
                            val date = isoFormat.parse(cleanDateStr)

                            if (date != null) {
                                val localCal = Calendar.getInstance()
                                localCal.time = date

                                val today = Calendar.getInstance()

                                if (localCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                    localCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {

                                    isAttendanceMarked = true
                                    val displayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    attendanceTime = displayFormat.format(localCal.time)
                                } else {
                                    isAttendanceMarked = false
                                    attendanceTime = null
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    isAttendanceMarked = false
                    attendanceTime = null
                }
            } catch (_: Exception) {
                // Ignore error or log it
            }
        }

        // Check attendance window
        // val calendar = Calendar.getInstance()
        // val hour = calendar.get(Calendar.HOUR_OF_DAY)
        // For testing, let's open the window for a larger time
        // val minute = calendar.get(Calendar.MINUTE)
        // isAttendanceWindowOpen = hour == 10 && minute <= 30
        // isAttendanceWindowOpen = true // Removed hardcoded value
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                fetchData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // User Icon
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                color = White,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "User",
                        modifier = Modifier.size(48.dp),
                        tint = Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Employee Portal",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                fontWeight = FontWeight.Bold
            )


            Text(
                text = if (userProfile != null) "Welcome ${userProfile?.name ?: "Employee"}" else "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                color = White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Group Info Card
            val statusIcon: androidx.compose.ui.graphics.vector.ImageVector
            val statusColor: Color
            val statusTitle: String
            val statusSubtitle: String

            if (userProfile?.groupId != null) {
                statusIcon = Icons.Default.CheckCircle
                statusColor = Success
                statusTitle = "Assigned Department"
                statusSubtitle = groupName
            } else if (userProfile?.requestStatus == "pending") {
                statusIcon = Icons.Default.Schedule
                statusColor = Warning
                statusTitle = "Request Pending"
                statusSubtitle = "Waiting for admin approval"
            } else if (userProfile?.requestStatus == "rejected") {
                statusIcon = Icons.Default.Cancel
                statusColor = Error
                statusTitle = "Request Rejected"
                statusSubtitle = "Please re-apply"
            } else {
                statusIcon = Icons.Default.Info
                statusColor = DarkGrey
                statusTitle = "Not Assigned"
                statusSubtitle = "No department assigned"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Black,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mark Attendance Button
            if (groupName != "Not Assigned to a Department") {
                Button(
                    onClick = {
                        if (userProfile?.requestStatus == "rejected") {
                            showReApplyDialog = true
                        } else {
                            navController.navigate("face_detection")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    enabled = (userProfile?.requestStatus == "rejected") || (isAttendanceWindowOpen && !isAttendanceMarked && userProfile?.groupId != null),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (userProfile?.requestStatus == "rejected") Error else Accent,
                        disabledContainerColor = DarkGrey
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    val (icon, text) = when {
                        userProfile?.requestStatus == "rejected" -> Pair(Icons.Default.Refresh, "Re-apply Now")
                        userProfile?.requestStatus == "pending" -> Pair(Icons.Default.HourglassEmpty, "Request Pending")
                        isAttendanceMarked -> Pair(Icons.Default.CheckCircle, "Attendance Marked at $attendanceTime")
                        isAttendanceWindowOpen -> Pair(Icons.Default.Face, "Mark Attendance")
                        else -> Pair(Icons.Default.Lock, "Window Closed")
                    }

                    Icon(icon, contentDescription = null, tint = White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            } else {
                Text(
                    text = "You cannot mark attendance until an admin assigns you to a department.",
                    color = White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Navigation - Profile
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                onClick = { navController.navigate("employee_profile") },
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        color = Primary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile",
                                modifier = Modifier.size(28.dp),
                                tint = Primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = "My Profile",
                            style = MaterialTheme.typography.titleMedium,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "View and edit your details",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkGrey
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = LightGrey,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showReApplyDialog && userProfile != null) {
        ReApplyDialog(
            userProfile = userProfile!!,
            availableGroups = availableGroups,
            onDismiss = { showReApplyDialog = false },
            onConfirm = { name, phone, tehsil, groupId ->
                scope.launch {
                    try {
                        val updatedProfile = userProfile!!.copy(
                            name = name,
                            phone = phone,
                            seating = tehsil,
                            requestedGroupId = groupId,
                            requestStatus = "pending"
                        )
                        supabase.from("profiles").upsert(updatedProfile)
                        Toast.makeText(context, "Re-applied successfully", Toast.LENGTH_SHORT).show()
                        showReApplyDialog = false
                        fetchData()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error re-applying: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReApplyDialog(
    userProfile: Profile,
    availableGroups: List<Group>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(userProfile.name ?: "") }
    var phone by remember { mutableStateOf(userProfile.phone ?: "") }
    var selectedTehsil by remember { mutableStateOf(userProfile.seating ?: "") }
    var selectedGroupId by remember { mutableStateOf(userProfile.requestedGroupId ?: "") }

    var tehsilExpanded by remember { mutableStateOf(false) }
    var groupExpanded by remember { mutableStateOf(false) }

    val tehsils = listOf("Sanchi", "Gairatganj", "Begamganj", "Silwani", "Udaipura", "Badi/bareli", "Obedullaganj")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Re-apply for Department",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Update your details to submit a new request.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkGrey,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = DarkGrey,
                        focusedLabelColor = Primary,
                        unfocusedLabelColor = DarkGrey,
                        focusedTextColor = Black,
                        unfocusedTextColor = Black,
                        cursorColor = Primary
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = DarkGrey,
                        focusedLabelColor = Primary,
                        unfocusedLabelColor = DarkGrey,
                        focusedTextColor = Black,
                        unfocusedTextColor = Black,
                        cursorColor = Primary
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Tehsil Dropdown
                ExposedDropdownMenuBox(
                    expanded = tehsilExpanded,
                    onExpandedChange = { tehsilExpanded = !tehsilExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedTehsil,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sitting (Tehsil)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tehsilExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = DarkGrey,
                            focusedLabelColor = Primary,
                            unfocusedLabelColor = DarkGrey,
                            focusedTextColor = Black,
                            unfocusedTextColor = Black,
                            cursorColor = Primary,
                            focusedTrailingIconColor = Primary,
                            unfocusedTrailingIconColor = DarkGrey
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = tehsilExpanded,
                        onDismissRequest = { tehsilExpanded = false },
                        containerColor = White
                    ) {
                        tehsils.forEach { tehsil ->
                            DropdownMenuItem(
                                text = { Text(tehsil, color = Black) },
                                onClick = {
                                    selectedTehsil = tehsil
                                    tehsilExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Group Dropdown
                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = { groupExpanded = !groupExpanded }
                ) {
                    val selectedGroupName = availableGroups.find { it.id == selectedGroupId }?.name ?: "Select Department"
                    OutlinedTextField(
                        value = selectedGroupName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Department") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = DarkGrey,
                            focusedLabelColor = Primary,
                            unfocusedLabelColor = DarkGrey,
                            focusedTextColor = Black,
                            unfocusedTextColor = Black,
                            cursorColor = Primary,
                            focusedTrailingIconColor = Primary,
                            unfocusedTrailingIconColor = DarkGrey
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false },
                        containerColor = White
                    ) {
                        availableGroups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name, color = Black) },
                                onClick = {
                                    selectedGroupId = group.id
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, phone, selectedTehsil, selectedGroupId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = White
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-apply Now", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = DarkGrey)
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
fun EmployeeProfileScreen(navController: NavController) {
    UserProfileScreen(navController = navController)
}
