package com.dbpsecurity.dbpcheckin.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.*
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Serializable
data class AttendanceRecord(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val timestamp: String, // timestamptz
    val status: String,
    val name: String? = null,
    val tehsil: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDownloadAttendanceScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supabase = SupabaseClient.client

    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var expanded by remember { mutableStateOf(false) }

    var startDateString by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        )
    }
    var endDateString by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        )
    }

    var isDownloading by remember { mutableStateOf(false) }
    var csvContent by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                    }
                    Toast.makeText(context, "File saved successfully", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            groups = supabase.from("groups").select().decodeList()
        } catch (e: Exception) {
            Toast.makeText(context, "Error fetching groups: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadAttendance() {
        if (selectedGroup == null) {
            Toast.makeText(context, "Please select a department", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = try {
            sdf.parse(startDateString)
        } catch (_: Exception) {
            Toast.makeText(context, "Invalid start date format. Use YYYY-MM-DD", Toast.LENGTH_SHORT).show()
            return
        }
        val endDate = try {
            sdf.parse(endDateString)
        } catch (_: Exception) {
            Toast.makeText(context, "Invalid end date format. Use YYYY-MM-DD", Toast.LENGTH_SHORT).show()
            return
        }

        if (startDate == null || endDate == null) return

        if (endDate.before(startDate)) {
            Toast.makeText(context, "End date cannot be before start date", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isDownloading = true
            try {
                // 1. Fetch members of the group
                val members = supabase.from("profiles").select {
                    filter { eq("group_id", selectedGroup!!.id) }
                }.decodeList<Profile>()

                if (members.isEmpty()) {
                    Toast.makeText(context, "No members in this department", Toast.LENGTH_SHORT).show()
                    isDownloading = false
                    return@launch
                }

                val memberIds = members.map { it.id }

                // 2. Fetch attendance for these members within the date range
                val startCalendar = Calendar.getInstance()
                startCalendar.time = startDate
                val startTimestamp = sdf.format(startCalendar.time)

                val endCalendar = Calendar.getInstance()
                endCalendar.time = endDate
                endCalendar.add(Calendar.DAY_OF_MONTH, 1) // Include the end date fully
                val endTimestamp = sdf.format(endCalendar.time)

                val attendanceRecords = supabase.from("attendance").select {
                    filter {
                        gte("created_at", startTimestamp)
                        lt("created_at", endTimestamp)
                    }
                }.decodeList<AttendanceRecord>()

                // Filter by group members
                val groupAttendance = attendanceRecords.filter { it.userId in memberIds }

                // 3. Generate Summary CSV
                val diff = endDate.time - startDate.time
                val totalDays = java.util.concurrent.TimeUnit.DAYS.convert(diff, java.util.concurrent.TimeUnit.MILLISECONDS) + 1

                val sb = StringBuilder()
                sb.append("Name,Seating,Phone,Email,Attendance\n")

                members.forEach { member ->
                    val memberAttendance = groupAttendance.filter { it.userId == member.id }

                    // Count unique days present
                    val uniqueDaysPresent = memberAttendance.mapNotNull { record ->
                        try {
                            // Extract YYYY-MM-DD from timestamp (e.g., 2023-10-27T10:00:00...)
                            if (record.timestamp.length >= 10) record.timestamp.substring(0, 10) else null
                        } catch (e: Exception) { null }
                    }.distinct().count()

                    val attendanceString = "$uniqueDaysPresent/$totalDays"

                    // Use formula syntax to prevent Excel date auto-formatting
                    sb.append("\"${member.name ?: "Unknown"}\",\"${member.seating ?: "N/A"}\",\"${member.phone ?: "N/A"}\",\"${member.email ?: "N/A"}\",\"=\"\"$attendanceString\"\"\"\n")
                }

                if (members.isEmpty()) {
                    Toast.makeText(context, "No members found", Toast.LENGTH_SHORT).show()
                } else {
                    csvContent = sb.toString()
                    launcher.launch("Attendance_Summary_${selectedGroup!!.name}_${startDateString}_to_${endDateString}.csv")
                }

            } catch (e: Exception) {
                Log.e("DownloadAttendance", "Error", e)
                Toast.makeText(context, "Error downloading attendance: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isDownloading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Attendance", color = White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Group Selection
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedGroup?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Department", color = White.copy(alpha = 0.9f)) },
                        placeholder = { Text("Select Department", color = White.copy(alpha = 0.7f)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Business, // Or Domain/Apartment if Business not available, let's try Business or Home
                                contentDescription = null,
                                tint = White
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = White,
                            unfocusedBorderColor = White.copy(alpha = 0.7f),
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedLabelColor = White,
                            unfocusedLabelColor = White.copy(alpha = 0.7f),
                            cursorColor = White,
                            focusedTrailingIconColor = White,
                            unfocusedTrailingIconColor = White.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = White
                    ) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name, color = Black) },
                                onClick = {
                                    selectedGroup = group
                                    expanded = false
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = startDateString,
                        onValueChange = { startDateString = it },
                        label = { Text("Start Date", color = White.copy(alpha = 0.7f)) },
                        placeholder = { Text("YYYY-MM-DD", color = White.copy(alpha = 0.5f)) },
                        trailingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = White)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = White,
                            unfocusedBorderColor = White.copy(alpha = 0.7f),
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            cursorColor = White,
                            focusedLabelColor = White,
                            unfocusedLabelColor = White.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = endDateString,
                        onValueChange = { endDateString = it },
                        label = { Text("End Date", color = White.copy(alpha = 0.7f)) },
                        placeholder = { Text("YYYY-MM-DD", color = White.copy(alpha = 0.5f)) },
                        trailingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = White)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = White,
                            unfocusedBorderColor = White.copy(alpha = 0.7f),
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            cursorColor = White,
                            focusedLabelColor = White,
                            unfocusedLabelColor = White.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { downloadAttendance() },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Download CSV", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                    }
                }
            }
        }
    }
}
