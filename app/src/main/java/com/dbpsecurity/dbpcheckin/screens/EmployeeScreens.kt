package com.dbpsecurity.dbpcheckin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.data.SessionManager
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

    val indiaTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    val indiaLocale = Locale("en", "IN")
    var userProfile by remember { mutableStateOf<Profile?>(SessionManager.currentUserProfile) }
    val isUnassignedStranded = userProfile?.groupId == null && userProfile?.requestStatus != "pending"
    var groupName by remember { mutableStateOf(SessionManager.currentUserGroup?.name ?: "Loading...") }
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
                popUpTo(0) { inclusive = true }
            }
            return
        }

        scope.launch {
            try {
                val profile = supabase.from("profiles").select {
                    filter { eq("id", userId) }
                }.decodeSingle<Profile>()
                userProfile = profile
                SessionManager.currentUserProfile = profile

                // Fetch all groups for re-apply dialog if needed
                // FIX: Allow re-apply if rejected OR if unassigned (and not pending)
                if (profile.requestStatus == "rejected" || (profile.groupId == null && profile.requestStatus != "pending")) {
                    val groups = supabase.from("groups").select().decodeList<Group>()
                    availableGroups = groups
                }

                if (profile.groupId != null && profile.groupId.isNotBlank()) {
                    val group = supabase.from("groups").select {
                        filter { eq("id", profile.groupId) }
                    }.decodeSingleOrNull<Group>()

                    if (group != null) {
                         SessionManager.currentUserGroup = group
                    }

                    groupName = group?.name ?: "Unknown Department"

                    if (group != null) {
                        try {
                            val now = Calendar.getInstance(indiaTimeZone)
                            val currentTime = now.time

                            fun parseTime(timeStr: String): Date? {
                                val formats = listOf("HH:mm:ss", "HH:mm")
                                for (format in formats) {
                                    try {
                                        val sdf = SimpleDateFormat(format, indiaLocale).apply { timeZone = indiaTimeZone }
                                        val date = sdf.parse(timeStr)
                                        if (date != null) {
                                            val cal = Calendar.getInstance(indiaTimeZone)
                                            cal.time = date

                                            val todayCal = Calendar.getInstance(indiaTimeZone)
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
                        // This covers the "Approved but Removed" or "Fresh User" state
                        groupName = "Not Assigned to a Department"
                    }
                }
            } catch (_: Exception) {
                groupName = "Error fetching data"
                // Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }

            // Check for existing attendance
            try {
                // Fetch the latest "active" or today's attendance
                // Logic: Find row where (check_in_date = today OR status = 'checked_in')
                // But simpler: just get the very latest record.
                val attendanceList = supabase.from("attendance").select {
                    filter { eq("user_id", userId) }
                    order("created_at", order = Order.DESCENDING) // created_at maps to check_in_time ideally
                    limit(1)
                }.decodeList<Attendance>()

                if (attendanceList.isNotEmpty()) {
                    val latest = attendanceList[0]
                    
                    // Determine State based on Status and CheckInTime
                    if (latest.status == "checked_in") {
                        // Active Session found!
                        isAttendanceMarked = true // Meaning "Session Active"
                        isAttendanceWindowOpen = false // Can't check in again
                        attendanceTime = latest.checkInTime // Store check-in time for countdown
                    } else {
                        // Session Closed (Present or Absent)
                        // Check if it belongs to TODAY
                        val checkInDateStr = latest.checkInDate
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        
                        if (checkInDateStr == todayStr) {
                             isAttendanceMarked = true // Marked for today
                             isAttendanceWindowOpen = false
                             attendanceTime = latest.checkInTime
                        } else {
                            // Old record, fresh start for today
                            isAttendanceMarked = false
                            attendanceTime = null
                            // Time window logic handles the boolean
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
                .verticalScroll(rememberScrollState())
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

            // Block Coordinator Dashboard Access
            if (userProfile?.groupId == "d3b85a72-ee90-45f3-a4c3-07fda71f743f") {
                Button(
                    onClick = {
                        scope.launch {
                            val profile = userProfile
                            if (profile != null && profile.role != "block_coordinator") {
                                try {
                                    supabase.from("profiles").update({
                                        set("role", "block_coordinator")
                                    }) {
                                        filter { eq("id", profile.id) }
                                    }
                                    // Update local session
                                    val updatedProfile = profile.copy(role = "block_coordinator")
                                    SessionManager.currentUserProfile = updatedProfile
                                    userProfile = updatedProfile
                                    Toast.makeText(context, "Role updated to Block Coordinator", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error updating role: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            navController.navigate("block_coordinator")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Icon(Icons.Default.Dashboard, contentDescription = null, tint = White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Go to Coordinator Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // NEW: Attendance Logic Block
            // 1. Determine State: "CHECK_IN", "COUNTDOWN", "CHECK_OUT", "DONE_FOR_DAY"
            // We use `isAttendanceMarked` as "Is Session Active OR Is Done For Day?"
            // Wait, we need finer granularity.
            // Let's rely on `attendanceTime` (CheckInTime) and `status` from latest record if available
            // but `fetchData` simplified it to booleans.

            // Let's parse `attendanceTime` back to Date to calc difference?
            // Actually, `attendanceTime` currently stores the string from DB. We need raw timestamp.
            // Let's assume we re-fetch relevant data or make `attendanceTime` hold the ISO string.
            
            // Re-refining the UI Logic here:
            val userGroup = SessionManager.currentUserGroup
            val currentStatus = if (isAttendanceMarked && attendanceTime != null) "checked_in_active" else if (isAttendanceMarked) "done_today" else "ready_to_check_in"
            
            // Since we simplified `isAttendanceMarked` logic above, let's fix it properly.
            // If `status == checked_in` -> `isAttendanceMarked` was true. `attendanceTime` has ISO string.
            // If `status == present` (today) -> `isAttendanceMarked` was true. `attendanceTime` has ISO string?
            
            // To make this robust without rewriting `fetchData` entirely, let's assume `attendanceTime` holds the CheckInTime string if set.
            
            if (groupName != "Not Assigned to a Department" || isUnassignedStranded) {
                
                // --- SHIFT LOGIC START ---
                if (userGroup?.isShiftBased == true && !isAttendanceMarked) {
                     // SHOW SHIFT SELECTOR
                     var selectedShift by remember { mutableStateOf<String?>(null) }
                     
                     Text("Select Your Shift", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                     Spacer(modifier = Modifier.height(8.dp))
                     
                     val shifts = listOf("Morning" to "08:00 AM", "Afternoon" to "02:00 PM", "Night" to "08:00 PM")
                     
                     
                     // Helper to check window
                     fun isWindowOpen(shift: String): Boolean {
                         val now = Calendar.getInstance(indiaTimeZone)
                         val hour = now.get(Calendar.HOUR_OF_DAY)
                         val minute = now.get(Calendar.MINUTE)
                         val timeInMinutes = hour * 60 + minute
                         
                         return when(shift) {
                             "Morning" -> timeInMinutes in (8 * 60)..(8 * 60 + 30) // 08:00 - 08:30
                             "Afternoon" -> timeInMinutes in (14 * 60)..(14 * 60 + 30) // 14:00 - 14:30
                             "Night" -> timeInMinutes in (20 * 60)..(20 * 60 + 30) // 20:00 - 20:30
                             else -> false
                         }
                     }

                     shifts.forEach { (shift, timeDesc) ->
                         val isOpen = isWindowOpen(shift)
                         val isSelected = (selectedShift == shift)
                         
                         // Determine visual assets
                         val (icon, iconColor) = when(shift) {
                             "Morning" -> Icons.Default.WbSunny to Color(0xFFFF9800) // Orange
                             "Afternoon" -> Icons.Default.WbCloudy to Color(0xFF03A9F4) // Light Blue
                             else -> Icons.Default.NightsStay to Color(0xFF673AB7) // Deep Purple
                         }
                         
                         // Card Styling - Premium Selection
                         val elevation = if (isSelected) 8.dp else 2.dp
                         
                         Card(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(vertical = 6.dp)
                                 .clip(RoundedCornerShape(16.dp))
                                 .clickable { selectedShift = shift },
                             colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                             border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                             elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                         ) {
                             Box(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .background(
                                         if (isSelected) {
                                             Brush.horizontalGradient(
                                                 colors = listOf(
                                                     Primary,
                                                     Primary.copy(alpha = 0.85f)
                                                 )
                                             )
                                         } else {
                                             SolidColor(White)
                                         }
                                     )
                                     .padding(16.dp)
                             ) {
                                 Row(
                                     modifier = Modifier.fillMaxWidth(),
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     // Icon Box
                                     Surface(
                                        modifier = Modifier.size(52.dp),
                                        shape = CircleShape,
                                        color = if (isSelected) White.copy(alpha = 0.2f) else iconColor.copy(alpha = 0.1f)
                                     ) {
                                         Box(contentAlignment = Alignment.Center) {
                                             Icon(
                                                 imageVector = icon,
                                                 contentDescription = null,
                                                 tint = if (isSelected) White else iconColor,
                                                 modifier = Modifier.size(28.dp)
                                             )
                                         }
                                     }
                                 
                                 Spacer(modifier = Modifier.width(16.dp))
                                 
                                 Column(modifier = Modifier.weight(1f)) {
                                     Text(
                                         text = shift, 
                                         style = MaterialTheme.typography.titleMedium,
                                         fontWeight = FontWeight.Bold,
                                         color = if (isSelected) White else DarkGrey
                                     )
                                     Text(
                                         text = timeDesc, 
                                         style = MaterialTheme.typography.bodySmall,
                                         color = if (isSelected) White.copy(alpha = 0.9f) else DarkGrey
                                     )
                                     // Window time display
                                     val windowTime = when(shift) {
                                         "Morning" -> "Window: 08:00 - 08:30"
                                         "Afternoon" -> "Window: 14:00 - 14:30"
                                         else -> "Window: 20:00 - 20:30"
                                     }
                                     Text(
                                         text = windowTime,
                                         style = MaterialTheme.typography.labelSmall,
                                         color = if (isSelected) White.copy(alpha = 0.9f) else DarkGrey
                                     )
                                 }
                                 
                                 // Checkmark for Selected OR Status Badge
                                 if (isSelected) {
                                     Surface(
                                         modifier = Modifier.size(32.dp),
                                         shape = CircleShape,
                                         color = White
                                     ) {
                                         Box(contentAlignment = Alignment.Center) {
                                             Icon(
                                                 imageVector = Icons.Default.Check,
                                                 contentDescription = "Selected",
                                                 tint = Primary,
                                                 modifier = Modifier.size(20.dp)
                                             )
                                         }
                                     }
                                 } else if (isOpen) {
                                     Surface(
                                         color = Success.copy(alpha = 0.1f),
                                         shape = RoundedCornerShape(50),
                                         border = androidx.compose.foundation.BorderStroke(1.dp, Success.copy(alpha = 0.3f))
                                     ) {
                                         Text(
                                            text = "OPEN",
                                            color = Success,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                         )
                                     }
                                 } else {
                                     Text(
                                        text = "CLOSED",
                                        color = DarkGrey,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                     )
                                 }
                             }
                         }
                     }
                     } // End of forEach loop

                     Spacer(modifier = Modifier.height(16.dp))
                     
                     val isSelectedWindowOpen = selectedShift != null && isWindowOpen(selectedShift!!)
                     
                     Button(
                         onClick = {
                             if (isSelectedWindowOpen) {
                                 navController.navigate("face_detection?actionType=CHECK_IN&shiftName=$selectedShift")
                             } else {
                                 Toast.makeText(context, "Check-in window is closed for this shift.", Toast.LENGTH_SHORT).show()
                             }
                         },
                         modifier = Modifier.fillMaxWidth().height(56.dp),
                         enabled = isSelectedWindowOpen,
                         shape = RoundedCornerShape(12.dp),
                         colors = ButtonDefaults.buttonColors(
                             containerColor = Primary,
                             disabledContainerColor = DarkGrey
                         )
                     ) {
                         Text(if (selectedShift == null) "Select a Shift" else if (isSelectedWindowOpen) "Check In ($selectedShift)" else "Window Closed")
                     }

                } else if (isAttendanceMarked) {
                    // --- ALREADY CHECKED IN (OR DONE) ---
                    // Calculate Elapsed Time
                    var elapsedTimeStr by remember { mutableStateOf("Calculaing...") }
                    var elapsedPercent by remember { mutableStateOf(0f) }
                    var canCheckOut by remember { mutableStateOf(false) }
                    var missedCheckout by remember { mutableStateOf(false) }
                    var isDoneForDay by remember { mutableStateOf(false) } // If status was 'present' or 'absent'

                    // We need to know if the status is 'checked_in' or 'present'/'absent'.
                    // Quick Hack: If 'attendanceTime' is present, we assume 'checked_in' if we can't see status. 
                    // But `fetchData` logic set `isAttendanceMarked=true` for Today's 'present' too.
                    // We really need the raw Status. Let's assume passed via a global or re-queried.
                    // For now, let's parse `attendanceTime` (ISO String) and compare with NOW.
                    
                    if (attendanceTime != null) {
                         LaunchedEffect(Unit) {
                             while(true) {
                                 try {
                                     val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                                     // Strip fractional seconds if present
                                     val cleanTime = if (attendanceTime!!.contains(".")) attendanceTime!!.substringBefore(".") else attendanceTime!!
                                     // Re-add T if lost? No, substringBefore keeps it. Wait, `2023-10T10:00:00.123` -> `2023-10T10:00:00`
                                     
                                     val checkInDate = isoFormat.parse(cleanTime)
                                     if (checkInDate != null) {
                                         val diff = System.currentTimeMillis() - checkInDate.time
                                         val hours = diff / (1000 * 60 * 60)
                                         val minutes = (diff / (1000 * 60)) % 60
                                         val seconds = (diff / 1000) % 60
                                         
                                         elapsedTimeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                                         elapsedPercent = (diff.toFloat() / (6 * 60 * 60 * 1000)).coerceIn(0f, 1f)
                                         
                                         if (hours >= 6 && hours < 10) {
                                             canCheckOut = true
                                             missedCheckout = false
                                         } else if (hours >= 10) {
                                             canCheckOut = false
                                             missedCheckout = true
                                         } else {
                                             canCheckOut = false
                                             missedCheckout = false
                                         }
                                     }
                                 } catch(e: Exception) {
                                     elapsedTimeStr = "Error"
                                 }
                                 kotlinx.coroutines.delay(1000)
                             }
                         }
                         
                         Card(
                             colors = CardDefaults.cardColors(containerColor = if (canCheckOut) Success else if (missedCheckout) Error else Primary),
                             modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                         ) {
                             Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                 Text(if (canCheckOut) "READY TO CHECKOUT" else if (missedCheckout) "MISSED CHECKOUT" else "SHIFT IN PROGRESS", 
                                     color = White, fontWeight = FontWeight.Bold)
                                 Text(elapsedTimeStr, style = MaterialTheme.typography.displayMedium, color = White)
                                 
                                 if (!canCheckOut && !missedCheckout) {
                                     LinearProgressIndicator(progress = { elapsedPercent }, modifier = Modifier.fillMaxWidth().padding(top=8.dp), color = White, trackColor = White.copy(alpha=0.3f))
                                     Text("6 Hours required", color = White.copy(alpha=0.8f), style = MaterialTheme.typography.bodySmall)
                                 }
                             }
                         }
                         
                         if (canCheckOut) {
                             Button(
                                 onClick = {
                                     navController.navigate("face_detection?actionType=CHECK_OUT")
                                 },
                                 modifier = Modifier.fillMaxWidth().height(56.dp),
                                 colors = ButtonDefaults.buttonColors(containerColor = Error) // Red for Checkout
                             ) {
                                 Text("CHECK OUT NOW")
                             }
                         }
                    } else {
                         // Fallback standard button if no time found (shouldn't happen if isMarked is true)
                         Button(onClick = {}, enabled = false) { Text("Attendance Marked Today") }
                    }

                } else {
                    // --- STANDARD CHECK IN (Legacy Logic) ---
                    Button(
                        onClick = {
                            if (userProfile?.requestStatus == "rejected" || isUnassignedStranded) {
                                showReApplyDialog = true
                            } else {
                                // Standard Mode CheckIn
                                navController.navigate("face_detection?actionType=CHECK_IN")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        // Enable if Rejected OR Stranded OR (Assigned AND Window Open)
                        enabled = (userProfile?.requestStatus == "rejected") || 
                                  isUnassignedStranded || 
                                  (isAttendanceWindowOpen && userProfile?.groupId != null),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (userProfile?.requestStatus == "rejected") Error else Accent,
                            disabledContainerColor = DarkGrey
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        val (icon, text) = when {
                            userProfile?.requestStatus == "rejected" -> Pair(Icons.Default.Refresh, "Re-apply Now")
                            userProfile?.requestStatus == "pending" && userProfile?.groupId == null -> Pair(Icons.Default.HourglassEmpty, "Request Pending")
                            isUnassignedStranded -> Pair(Icons.Default.PersonAdd, "Apply for Department")
                            isAttendanceWindowOpen -> Pair(Icons.Default.Face, "Check In")
                            else -> Pair(Icons.Default.Lock, "Check-in Window Closed")
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
                }
            } else {
                Text(
                    text = "You cannot mark attendance until an admin assigns you to a department.",
                    color = White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Block Coordinator Dashboard Button
            if (userProfile?.role == "block_coordinator") {
                Button(
                    onClick = { navController.navigate("block_coordinator") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Secondary,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Icon(Icons.Default.Dashboard, contentDescription = null, tint = White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Go to Block Coordinator Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
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

    val tehsils = listOf("Sanchi", "Gairatganj", "Begamganj", "Silwani", "Udaipura", "Badi/bareli", "Obedullaganj", "Raisen", "Sultanganj")

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
