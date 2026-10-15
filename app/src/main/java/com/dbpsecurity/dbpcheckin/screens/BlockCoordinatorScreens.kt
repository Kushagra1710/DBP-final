package com.dbpsecurity.dbpcheckin.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Attendance
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class AttendanceSimple(
    @SerialName("user_id") val userId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String // Add this field for client-side validation
)

// This was the original BlockCoordinatorScreen, now renamed to Dashboard
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockAttendanceDashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    val indiaTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    val indiaLocale = Locale("en", "IN")

    var selectedDate by remember { mutableStateOf(Calendar.getInstance(indiaTimeZone).time) }
    var showDatePicker by remember { mutableStateOf(false) }

    var presentListIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var absentListIds by remember { mutableStateOf<List<String>>(emptyList()) }
    // Cache to store loaded profiles. Key: UserID, Value: Profile
    val profileCache = remember { mutableStateMapOf<String, Profile>() }
    // Track which IDs are currently being fetched to avoid duplicate requests
    val fetchingIds = remember { mutableStateListOf<String>() }

    var isLoading by remember { mutableStateOf(false) }
    var blockName by remember { mutableStateOf("Loading...") }
    var attendanceTimeMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) } 
    var attendanceStatusMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // New: Store status string

    var selectedEmployee by remember { mutableStateOf<Profile?>(null) }
    var groupsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val displayDateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", indiaLocale).apply { timeZone = indiaTimeZone }

    fun fetchProfileDetails(userIds: List<String>) {
        val idsToFetch = userIds.filter { !profileCache.containsKey(it) && !fetchingIds.contains(it) }
        if (idsToFetch.isEmpty()) return

        scope.launch {
            try {
                fetchingIds.addAll(idsToFetch)
                // Fetch in chunks of 20 to be safe
                idsToFetch.chunked(20).forEach { batch ->
                    val profiles = supabase.from("profiles").select {
                        filter { isIn("id", batch) }
                    }.decodeList<Profile>()

                    profiles.forEach { profile ->
                        profileCache[profile.id] = profile
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                fetchingIds.removeAll(idsToFetch)
            }
        }
    }

    fun fetchAttendance(date: Date) {
        scope.launch {
            isLoading = true
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    // 1. Get Coordinator Profile to know Seating
                    // We need full profile here as it's just one
                    val coordinator = supabase.from("profiles").select {
                        filter { eq("id", userId) }
                    }.decodeSingleOrNull<Profile>()

                    val seating = coordinator?.seating
                    blockName = seating ?: "Unknown Block"

                    if (seating != null) {
                        // Fetch groups mapping
                        val groups = supabase.from("groups").select().decodeList<Group>()
                        groupsMap = groups.associate { it.id to it.name }

                        // 2. Fetch ALL user IDs in this seating (Optimization: ID ONLY)
                        // This corresponds to Step 1 of Optimization Plan
                        val allUserIds = supabase.from("profiles").select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                            filter {
                                eq("tehsil", seating)
                            }
                        }.decodeList<JsonObject>().map { it["id"]?.jsonPrimitive?.contentOrNull ?: "" }.filter { it.isNotEmpty() }

                        // 3. Fetch attendance for this date and seating
                        // We filter attendance by date range of the selected day
                        val calendar = Calendar.getInstance(indiaTimeZone)
                        calendar.time = date

                        // Set to Start of Day (Local Time)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        val startOfDayDate = calendar.time

                        // Set to End of Day (Local Time - actually Start of Next Day)
                        val endCalendar = Calendar.getInstance(indiaTimeZone)
                        endCalendar.time = startOfDayDate
                        endCalendar.add(Calendar.DAY_OF_MONTH, 1)
                        val endOfDayDate = endCalendar.time

                        // Convert to ISO 8601 with Offset for precise querying
                        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", indiaLocale) // e.g., 2025-12-25T00:00:00+05:30
                        isoFormat.timeZone = indiaTimeZone
                        val startOfDayStr = isoFormat.format(startOfDayDate)
                        val endOfDayStr = isoFormat.format(endOfDayDate)

                        val attendanceRecords = supabase.from("attendance").select {
                            filter {
                                // Filter strictly by date range
                                gte("created_at", startOfDayStr)
                                lt("created_at", endOfDayStr)
                                // Fetch BOTH completed (present) and running (checked_in)
                                isIn("status", listOf("present", "checked_in"))
                            }
                        }.decodeList<AttendanceSimple>()

                        // Filter strictly by user IDs that belong to this tehsil
                        val authorizedUserIdsSet = allUserIds.toSet()

                        // Parse helper for server timestamp
                        val serverTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) // Standard ISO start
                        serverTimeFormat.timeZone = TimeZone.getTimeZone("UTC") // Database usually returns UTC

                        // Explicitly check for overlaps client-side to ensure server filter didn't fail
                        val validRecords = attendanceRecords
                            .filter { record ->
                                if (record.userId !in authorizedUserIdsSet) return@filter false

                                // Client-side date check
                                try {
                                    val safeTimeString = if (record.createdAt.length > 19) record.createdAt.substring(0, 19) else record.createdAt
                                    // Assume UTC for parsing
                                    val recordDate = serverTimeFormat.parse(safeTimeString)

                                    if (recordDate != null) {
                                        // Compare millis
                                        recordDate.time >= startOfDayDate.time && recordDate.time < endOfDayDate.time
                                    } else {
                                        false
                                    }
                                } catch (e: Exception) {
                                    // If parsing fails, fall back to trusting server (or exclude unsafe record)
                                    true
                                }
                            }

                        val presentUserIds = validRecords.map { it.userId }.toSet()

                        // Create map of times
                        val displayTimeFormat = SimpleDateFormat("hh:mm a", indiaLocale).apply { timeZone = indiaTimeZone }
                        attendanceTimeMap = validRecords.associate { record ->
                            val safeTimeString = if (record.createdAt.length > 19) record.createdAt.substring(0, 19) else record.createdAt
                            val recordDate = try { serverTimeFormat.parse(safeTimeString) } catch(_: Exception) { null }
                            val timeStr = if (recordDate != null) displayTimeFormat.format(recordDate) else "--:--"
                            record.userId to timeStr
                        }

                        // Map UserID -> Status (checked_in vs present)
                        attendanceStatusMap = validRecords.associate { it.userId to it.status }

                        presentListIds = allUserIds.filter { it in presentUserIds }
                        absentListIds = allUserIds.filter { it !in presentUserIds }

                        // Optimization: Fetch ALL profiles in batches immediately
                        fetchProfileDetails(allUserIds)

                    } else {
                        Toast.makeText(context, "No seating assigned to you.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchAttendance(selectedDate)
    }

    if (showDatePicker) {
        val calendar = Calendar.getInstance(indiaTimeZone)
        calendar.time = selectedDate
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newDate = Calendar.getInstance(indiaTimeZone)
                newDate.set(year, month, dayOfMonth, 0, 0, 0)
                newDate.set(Calendar.MILLISECOND, 0) // Explicitly zero out time
                selectedDate = newDate.time
                showDatePicker = false
                fetchAttendance(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
        showDatePicker = false
    }

    if (selectedEmployee != null) {
        EmployeeDetailDialog(
            employee = selectedEmployee!!,
            groupName = groupsMap[selectedEmployee!!.groupId] ?: "Checking...",
            onDismiss = { selectedEmployee = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Block Attendance: $blockName") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = White
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
                    }
                }
            )
        }
    ) { padding ->
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Date Filter Card (White, Clean, Professional)
                    Card(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = Primary.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Selected Date",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DarkGrey
                                    )
                                    Text(
                                        text = displayDateFormatter.format(selectedDate),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Secondary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Change",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Secondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = White)
                        }
                    }
                } else {
                    val total = presentListIds.size + absentListIds.size

                    // Present Section Header
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Success),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Present",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    color = White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${presentListIds.size} / $total",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Present List Items
                    if (presentListIds.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No employee present",
                                    color = White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        items(presentListIds) { userId ->
                            val userProfile = profileCache[userId]
                            if (userProfile != null) {
                                AttendanceUserCard(
                                    user = userProfile,
                                    status = attendanceStatusMap[userId] ?: "present",
                                    timeMarked = attendanceTimeMap[userId]
                                ) {
                                    selectedEmployee = userProfile
                                }
                            } else {
                                // LaunchedEffect removed: Data is pre-fetched in batches
                                AttendanceUserCardLoading()
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }

                    // Absent Section Header
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Absent",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    color = White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${absentListIds.size} / $total",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Absent List Items
                    if (absentListIds.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Everyone is present!",
                                    color = White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                    items(absentListIds) { userId ->
                        val userProfile = profileCache[userId]
                        if (userProfile != null) {
                            AttendanceUserCard(
                                user = userProfile,
                                status = "absent"
                            ) {
                                selectedEmployee = userProfile
                            }
                        } else {
                            // LaunchedEffect removed: Data is pre-fetched in batches
                            AttendanceUserCardLoading()
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    }
                }
            }
        }
    }
}

// The New Block Coordinator Home Screen (Employee Screen + Dashboard Button)
@Composable
fun BlockCoordinatorScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    val indiaTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    val indiaLocale = Locale("en", "IN")

    var userProfile by remember { mutableStateOf<Profile?>(null) }
    var groupName by remember { mutableStateOf("Loading...") }
    var isAttendanceWindowOpen by remember { mutableStateOf(false) }
    var isAttendanceMarked by remember { mutableStateOf(false) }
    var isCheckedOut by remember { mutableStateOf(false) }
    var attendanceStatus by remember { mutableStateOf<String?>(null) }
    var attendanceTime by remember { mutableStateOf<String?>(null) }
    var attendanceTimestamp by remember { mutableStateOf<String?>(null) } // Added for Timer

    // Re-apply logic state
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
                if (profile.requestStatus == "rejected" || (profile.groupId == null && profile.requestStatus != "pending")) {
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
                        groupName = "Not Assigned to a Department"
                    }
                }
            } catch (_: Exception) {
                groupName = "Error fetching data"
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
                            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", indiaLocale)
                            isoFormat.timeZone = TimeZone.getTimeZone("UTC") // Database returns UTC

                            val cleanDateStr = if (createdAt.length >= 19) createdAt.substring(0, 19) else createdAt
                            val date = isoFormat.parse(cleanDateStr)

                            if (date != null) {
                                val localCal = Calendar.getInstance(indiaTimeZone)
                                localCal.time = date
                                val today = Calendar.getInstance(indiaTimeZone)

                                if (localCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                    localCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {

                                    isAttendanceMarked = true
                                    attendanceTimestamp = latest.createdAt 
                                    attendanceStatus = latest.status
                                    isCheckedOut = latest.status != "checked_in"

                                    val displayFormat = SimpleDateFormat("hh:mm a", indiaLocale).apply { timeZone = indiaTimeZone }
                                    attendanceTime = displayFormat.format(localCal.time)
                                } else {
                                    isAttendanceMarked = false
                                    attendanceTime = null
                                    attendanceTimestamp = null
                                    isCheckedOut = false
                                    attendanceStatus = null
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
                // Ignore
            }
        }
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
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
                text = "Block Coordinator Panel",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (userProfile != null) "Hi ${userProfile?.name ?: "Coordinator"}" else "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                color = White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Status Card (Similar to Employee)
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
            val isUnassignedStranded = userProfile?.groupId == null && userProfile?.requestStatus != "pending"

            if (groupName != "Not Assigned to a Department" || isUnassignedStranded) {
                if (isAttendanceMarked && attendanceTimestamp != null) {
                    
                    if (isCheckedOut) {
                         // --- ALREADY CHECKED OUT (PRESENT/ABSENT) UI ---
                         Card(
                            colors = CardDefaults.cardColors(containerColor = if (attendanceStatus == "present") Success else Error),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (attendanceStatus == "present") Icons.Default.CheckCircle else Icons.Default.Cancel, 
                                    contentDescription = null, 
                                    tint = White, 
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (attendanceStatus == "present") "ATTENDANCE COMPLETED" else "MARKED ABSENT", 
                                    color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Status: ${attendanceStatus?.replaceFirstChar { it.uppercase() } ?: "Unknown"}",
                                    color = White.copy(alpha=0.9f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        // --- LIVE TIMER UI (SHIFT IN PROGRESS) ---
                        var elapsedTimeStr by remember { mutableStateOf("Calculating...") }
                        var elapsedPercent by remember { mutableStateOf(0f) }
                        var canCheckOut by remember { mutableStateOf(false) }
                        
                        LaunchedEffect(attendanceTimestamp) {
                            while(true) {
                                try {
                                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                                    val cleanTime = if (attendanceTimestamp!!.contains(".")) attendanceTimestamp!!.substringBefore(".") else attendanceTimestamp!!
                                    val checkInDate = isoFormat.parse(cleanTime)
                                    
                                    if (checkInDate != null) {
                                        val diff = System.currentTimeMillis() - checkInDate.time
                                        val hours = diff / (1000 * 60 * 60)
                                        val minutes = (diff / (1000 * 60)) % 60
                                        val seconds = (diff / 1000) % 60
                                        
                                        elapsedTimeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                                        elapsedPercent = (diff.toFloat() / (8 * 60 * 60 * 1000)).coerceIn(0f, 1f)
                                        
                                        canCheckOut = hours >= 8
                                    }
                                } catch(e: Exception) {
                                    elapsedTimeStr = "Error"
                                }
                                kotlinx.coroutines.delay(1000)
                            }
                        }
    
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (canCheckOut) Success else Primary),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (canCheckOut) "READY TO CHECKOUT" else "SHIFT IN PROGRESS", 
                                    color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(elapsedTimeStr, style = MaterialTheme.typography.displayMedium, color = White, fontWeight = FontWeight.Bold)
                                
                                if (!canCheckOut) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = { elapsedPercent }, 
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), 
                                        color = White, 
                                        trackColor = White.copy(alpha=0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("8 Hours required for checkout", color = White.copy(alpha=0.8f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
    
                        if (canCheckOut) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { navController.navigate("face_detection?actionType=CHECK_OUT") },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CHECK OUT NOW", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                } else {
                    // --- STANDARD CHECK IN BUTTON ---
                    Button(
                        onClick = {
                            if (userProfile?.requestStatus == "rejected" || isUnassignedStranded) {
                                showReApplyDialog = true
                            } else {
                                navController.navigate("face_detection")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        enabled = (userProfile?.requestStatus == "rejected") || 
                                  isUnassignedStranded || 
                                  (isAttendanceWindowOpen && !isAttendanceMarked && userProfile?.groupId != null),
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
                            userProfile?.requestStatus == "pending" && userProfile?.groupId == null -> Pair(Icons.Default.HourglassEmpty, "Request Pending")
                            isUnassignedStranded -> Pair(Icons.Default.PersonAdd, "Apply for Department")
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
                }
            } else {
                 Text(
                    text = "You cannot mark attendance until an admin assigns you to a department.",
                    color = White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // View Block Attendance Card
            Card(
                onClick = { navController.navigate("block_attendance_dashboard") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = White
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Secondary, Primary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = null,
                            tint = White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "View Attendance",
                            style = MaterialTheme.typography.titleLarge,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Check daily staff presence",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkGrey
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = LightGrey,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Navigation - Profile
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                onClick = { navController.navigate("employee_profile") }, 
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Secondary, Primary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.size(28.dp),
                            tint = White
                        )
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

    // Re-apply logic reuse
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

@Composable
fun AttendanceUserCardLoading() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = White.copy(alpha = 0.8f) // Slightly more transparent to indicate loading
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skeleton Avatar
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = LightGrey.copy(alpha = 0.3f)
            ) {}

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Skeleton Text
                Surface(
                    modifier = Modifier.width(120.dp).height(20.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = LightGrey.copy(alpha = 0.3f)
                ) {}
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.width(80.dp).height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = LightGrey.copy(alpha = 0.3f)
                ) {}
            }
        }
    }
}

@Composable
fun AttendanceUserCard(user: Profile, status: String, timeMarked: String? = null, onClick: () -> Unit) {
    // Define styles based on status
    val cardColor = when (status) {
        "checked_in" -> Warning.copy(alpha = 0.1f) // Light Orange for In Process
        "present" -> Success.copy(alpha = 0.1f)    // Light Green for Completed
        else -> Error.copy(alpha = 0.1f)           // Light Red for Absent
    }
    
    val statusColor = when (status) {
        "checked_in" -> Warning
        "present" -> Success
        else -> Error
    }

    val icon = when (status) {
        "checked_in" -> Icons.Default.Schedule       // Clock for In Process
        "present" -> Icons.Default.CheckCircle       // Check for Done
        else -> Icons.Default.Cancel                 // Cancel for Absent
    }

    val statusText = when (status) {
        "checked_in" -> "Working (In Process)"
        "present" -> "Shift Completed"
        else -> "Absent"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clickable { onClick() }
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar / Icon with Status Ring
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = cardColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!user.imageUrl.isNullOrEmpty()) {
                             Icon(
                                Icons.Default.Person, contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Text(
                                text = user.name?.take(1)?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }
                
                // Status Indicator Dot
                Surface(
                    modifier = Modifier.size(14.dp).padding(2.dp),
                    shape = CircleShape,
                    color = White
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if ((status == "checked_in" || status == "present") && timeMarked != null) 
                                "Marked at $timeMarked ($statusText)" 
                               else statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkGrey,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
             Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Details",
                modifier = Modifier.size(14.dp),
                tint = LightGrey
            )
        }
    }
}

@Composable
fun EmployeeDetailDialog(
    employee: Profile,
    groupName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Employee Details",
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
                InfoRow(icon = Icons.Default.Face, label = "Name", value = employee.name ?: "N/A")
                InfoRow(icon = Icons.Default.Email, label = "Email", value = employee.email ?: "N/A")
                InfoRow(icon = Icons.Default.Phone, label = "Phone", value = employee.phone ?: "N/A")
                InfoRow(icon = Icons.Default.Info, label = "Department", value = groupName)
                InfoRow(icon = Icons.Default.Info, label = "Seating", value = employee.seating ?: "N/A")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Primary)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DarkGrey,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = DarkGrey
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = Black,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
