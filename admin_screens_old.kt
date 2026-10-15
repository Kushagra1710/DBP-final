package com.dbpsecurity.dbpcheckin.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.*
import com.dbpsecurity.dbpcheckin.ui.theme.Error as ErrorColor
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AdminHomeScreen(navController: NavController) {
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
            Surface(
                modifier = Modifier.size(80.dp).clip(CircleShape),
                color = White,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Admin",
                        modifier = Modifier.size(48.dp),
                        tint = Primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Admin Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Manage your organization",
                style = MaterialTheme.typography.bodyMedium,
                color = White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                onClick = { navController.navigate("admin_management") },
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(70.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Primary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Manage",
                                modifier = Modifier.size(40.dp),
                                tint = Primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage Departments",
                            style = MaterialTheme.typography.titleLarge,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add, edit, and organize employee departments",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkGrey
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                onClick = { navController.navigate("admin_profile") },
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(70.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Accent.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Profile",
                                modifier = Modifier.size(40.dp),
                                tint = Accent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Profile",
                            style = MaterialTheme.typography.titleLarge,
                            color = Accent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "View and edit your account details",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkGrey
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                onClick = { navController.navigate("admin_requests") },
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(70.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Secondary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Requests",
                                modifier = Modifier.size(40.dp),
                                tint = Secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Department Requests",
                            style = MaterialTheme.typography.titleLarge,
                            color = Secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Approve or reject department join requests",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkGrey
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                onClick = { navController.navigate("admin_download_attendance") },
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(70.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Primary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Download",
                                modifier = Modifier.size(40.dp),
                                tint = Primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download Attendance",
                            style = MaterialTheme.typography.titleLarge,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Export attendance records to CSV",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkGrey
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) } // New State
    var groupToDelete by remember { mutableStateOf<Group?>(null) } // New State
    var groupToEdit by remember { mutableStateOf<Group?>(null) }

    var newGroupName by remember { mutableStateOf("") }
    var newGroupLat by remember { mutableStateOf("") }
    var newGroupLong by remember { mutableStateOf("") }
    var newGroupRadius by remember { mutableStateOf("100.0") }
    var newGroupStartTime by remember { mutableStateOf("") }
    var newGroupEndTime by remember { mutableStateOf("") }
    var newGroupIsLocationRestricted by remember { mutableStateOf(true) } // New State
    var selectedMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showSeatingSelectionDialog by remember { mutableStateOf(false) }
    var currentSeatingName by remember { mutableStateOf("") }
    var currentSeatingUsers by remember { mutableStateOf<List<Profile>>(emptyList()) }

    var editGroupName by remember { mutableStateOf("") }
    var editGroupLat by remember { mutableStateOf("") }
    var editGroupLong by remember { mutableStateOf("") }
    var editGroupRadius by remember { mutableStateOf("") }
    var editGroupStartTime by remember { mutableStateOf("") }
    var editGroupEndTime by remember { mutableStateOf("") }
    var editGroupIsLocationRestricted by remember { mutableStateOf(true) } // New State
    var editGroupMembers by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var editGroupSelectedNewMembers by remember { mutableStateOf<Set<String>>(emptySet()) }

    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var unassignedUsers by remember { mutableStateOf<List<Profile>>(emptyList()) }

    suspend fun fetchAllData() {
        try {
            groups = supabase.from("groups").select().decodeList()
            val allEmployees = supabase.from("profiles").select {
                filter {
                    eq("role", "employee")
                }
            }.decodeList<Profile>()

            unassignedUsers = allEmployees.filter { it.groupId == null }
        } catch (e: Exception) {
            Toast.makeText(context, "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        fetchAllData()
        val channel = supabase.channel("db-changes")
        scope.launch {
            channel.postgresChangeFlow<PostgresAction>(schema = "public").collectLatest {
                fetchAllData()
            }
        }
        channel.subscribe()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newGroupName = ""
                    newGroupLat = ""
                    newGroupLong = ""
                    newGroupRadius = "100.0"
                    newGroupStartTime = ""
                    newGroupEndTime = ""
                    newGroupIsLocationRestricted = true // Reset
                    selectedMemberIds = emptySet()
                    showAddGroupDialog = true
                },
                containerColor = Accent,
                contentColor = White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Department")
            }
        }
    ) { padding ->
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
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Manage Departments",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(groups) { group ->
                    GroupCard(
                        group = group,
                        onEditClick = {
                            groupToEdit = it
                            editGroupName = it.name
                            editGroupLat = it.latitude.toString()
                            editGroupLong = it.longitude.toString()
                            editGroupRadius = it.radius.toString()
                            editGroupStartTime = it.startTime
                            editGroupEndTime = it.endTime
                            editGroupIsLocationRestricted = it.isLocationRestricted // Load
                            editGroupSelectedNewMembers = emptySet()

                            scope.launch {
                                try {
                                    Log.d("AdminScreens", "Fetching members for group: ${it.id}")
                                    val members = supabase.from("profiles").select {
                                        filter { eq("group_id", it.id) }
                                    }.decodeList<Profile>()
                                    Log.d("AdminScreens", "Fetched ${members.size} members for group ${it.id}")
                                    editGroupMembers = members
                                    showEditGroupDialog = true
                                } catch (e: Exception) {
                                    Log.e("AdminScreens", "Error fetching group members", e)
                                    Toast.makeText(context, "Error fetching members: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteClick = {
                            groupToDelete = it
                            showDeleteGroupDialog = true // Show delete confirmation dialog
                        }
                    )
                }
            }
        }
    }

    if (showAddGroupDialog) {
        GroupDialog(
            title = "Create New Department",
            groupName = newGroupName, onGroupNameChange = { newGroupName = it },
            lat = newGroupLat, onLatChange = { newGroupLat = it },
            long = newGroupLong, onLongChange = { newGroupLong = it },
            radius = newGroupRadius, onRadiusChange = { newGroupRadius = it },
            startTime = newGroupStartTime, onStartTimeChange = { newGroupStartTime = it },
            endTime = newGroupEndTime, onEndTimeChange = { newGroupEndTime = it },
            isLocationRestricted = newGroupIsLocationRestricted, onLocationRestrictedChange = { newGroupIsLocationRestricted = it }, // Pass
            availableUsers = unassignedUsers,
            selectedUserIds = selectedMemberIds,
            onUserSelectionChanged = { userId, isSelected ->
                selectedMemberIds = if (isSelected) selectedMemberIds + userId else selectedMemberIds - userId
            },
            onDismiss = { showAddGroupDialog = false },
            onConfirm = {
                if (newGroupName.isNotEmpty()) { // Relaxed validation for unrestricted
                    scope.launch {
                        try {
                            val newGroup = Group(
                                id = UUID.randomUUID().toString(),
                                name = newGroupName,
                                latitude = newGroupLat.toDoubleOrNull() ?: 0.0,
                                longitude = newGroupLong.toDoubleOrNull() ?: 0.0,
                                startTime = newGroupStartTime,
                                endTime = newGroupEndTime,
                                radius = newGroupRadius.toDoubleOrNull() ?: 100.0,
                                isLocationRestricted = newGroupIsLocationRestricted // Save
                            )
                            Log.d("AdminScreens", "Creating group: ${newGroup.id}")
                            supabase.from("groups").insert(listOf(newGroup))

                            if (selectedMemberIds.isNotEmpty()) {
                                Log.d("AdminScreens", "Assigning members: $selectedMemberIds to group ${newGroup.id}")
                                try {
                                    var successCount = 0
                                    selectedMemberIds.forEach { memberId ->
                                        try {
                                            val updated = supabase.from("profiles").update({ "group_id" to newGroup.id }) {
                                                filter { eq("id", memberId) }
                                                select()
                                            }.decodeList<Profile>()

                                            if (updated.isNotEmpty()) {
                                                successCount++
                                                Log.d("AdminScreens", "Updated member $memberId. New group_id: ${updated[0].groupId}")
                                            } else {
                                                Log.e("AdminScreens", "Update returned empty for member $memberId. Trying RPC fallback...")
                                                try {
                                                    val params = mapOf("user_id" to memberId, "new_group_id" to newGroup.id)
                                                    supabase.postgrest.rpc("assign_user_to_group", params)
                                                    successCount++
                                                    Log.d("AdminScreens", "RPC update successful for member $memberId")
                                                } catch (rpcError: Exception) {
                                                    Log.e("AdminScreens", "RPC fallback failed for member $memberId", rpcError)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AdminScreens", "Failed to update member $memberId", e)
                                        }
                                    }
                                    Log.d("AdminScreens", "Successfully assigned $successCount members to group ${newGroup.id}")
                                } catch (e: Exception) {
                                    Log.e("AdminScreens", "Failed to assign members loop", e)
                                    throw e
                                }
                            } else {
                                Log.d("AdminScreens", "No members selected for new group")
                            }
                            showAddGroupDialog = false
                            fetchAllData()
                        } catch (e: Exception) {
                            Log.e("AdminScreens", "Error creating group", e)
                            Toast.makeText(context, "Error creating group: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    if (showEditGroupDialog && groupToEdit != null) {
        val group = groupToEdit!!
        GroupDialog(
            isEdit = true,
            title = "Edit Department",
            groupName = editGroupName,
            onGroupNameChange = { editGroupName = it },
            lat = editGroupLat,
            onLatChange = { editGroupLat = it },
            long = editGroupLong,
            onLongChange = { editGroupLong = it },
            radius = editGroupRadius,
            onRadiusChange = { editGroupRadius = it },
            startTime = editGroupStartTime,
            onStartTimeChange = { editGroupStartTime = it },
            endTime = editGroupEndTime,
            onEndTimeChange = { editGroupEndTime = it },
            isLocationRestricted = editGroupIsLocationRestricted, onLocationRestrictedChange = { editGroupIsLocationRestricted = it }, // Pass
            availableUsers = unassignedUsers,
            selectedUserIds = editGroupSelectedNewMembers,
            onUserSelectionChanged = { userId, isSelected ->
                editGroupSelectedNewMembers = if (isSelected) editGroupSelectedNewMembers + userId else editGroupSelectedNewMembers - userId
            },
            currentMembers = editGroupMembers,
            onRemoveMember = { memberId ->
                val memberToRemove = editGroupMembers.find { it.id == memberId }
                scope.launch {
                    try {
                        Log.d("AdminScreens", "Removing member $memberId from group")

                        // Use the NEW unique function name to avoid conflicts
                        val params = mapOf("p_user_id" to memberId)
                        supabase.postgrest.rpc("admin_remove_user", params)

                        Log.d("AdminScreens", "Member $memberId removed successfully")
                        editGroupMembers = editGroupMembers.filter { it.id != memberId }
                        if (memberToRemove != null) {
                            unassignedUsers = unassignedUsers + memberToRemove.copy(groupId = null)
                        }
                        Toast.makeText(context, "Member removed from department", Toast.LENGTH_SHORT).show()

                    } catch (e: Exception) {
                        Log.e("AdminScreens", "Error removing member", e)
                        Toast.makeText(context, "Error removing member: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showEditGroupDialog = false },
            onConfirm = {
                val lat = editGroupLat.toDoubleOrNull() ?: 0.0
                val long = editGroupLong.toDoubleOrNull() ?: 0.0
                val rad = editGroupRadius.toDoubleOrNull() ?: 100.0

                // Validation logic: if restricted, lat/long must be valid
                if (editGroupIsLocationRestricted && (lat == 0.0 || long == 0.0)) {
                     Toast.makeText(context, "Invalid Latitude or Longitude for restricted group", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        try {
                            Log.d("AdminScreens", "Updating group: ${group.id}")

                            // Use upsert to ensure it updates the existing record
                            val groupToUpdate = Group(
                                id = group.id,
                                name = editGroupName,
                                latitude = lat,
                                longitude = long,
                                startTime = editGroupStartTime,
                                endTime = editGroupEndTime,
                                radius = rad,
                                isLocationRestricted = editGroupIsLocationRestricted // Save
                            )

                            val updatedGroup = supabase.from("groups").upsert(groupToUpdate) {
                                select()
                            }.decodeSingleOrNull<Group>()

                            if (updatedGroup != null) {
                                Log.d("AdminScreens", "Group updated successfully: $updatedGroup")
                                Toast.makeText(context, "Department updated successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e("AdminScreens", "Group update returned null. ID: ${group.id}")
                                // Fallback: Try standard update if upsert returned null (unlikely but possible)
                                supabase.from("groups").update({
                                    "name" to editGroupName
                                    "latitude" to lat
                                    "longitude" to long
                                    "radius" to rad
                                    "start_time" to editGroupStartTime
                                    "end_time" to editGroupEndTime
                                }) {
                                    filter { eq("id", group.id) }
                                }
                                Toast.makeText(context, "Group update attempted (check list)", Toast.LENGTH_SHORT).show()
                            }

                            if (editGroupSelectedNewMembers.isNotEmpty()) {
                                Log.d("AdminScreens", "Assigning new members: $editGroupSelectedNewMembers to group ${group.id}")
                                try {
                                    var successCount = 0
                                    editGroupSelectedNewMembers.forEach { memberId ->
                                        try {
                                            val updated = supabase.from("profiles").update({ "group_id" to group.id }) {
                                                filter { eq("id", memberId) }
                                                select()
                                            }.decodeList<Profile>()

                                            if (updated.isNotEmpty()) {
                                                successCount++
                                                Log.d("AdminScreens", "Updated member $memberId. New group_id: ${updated[0].groupId}")
                                            } else {
                                                Log.e("AdminScreens", "Update returned empty for member $memberId. Trying RPC fallback...")
                                                try {
                                                    // Use the NEW unique function name
                                                    val params = mapOf("p_user_id" to memberId, "p_group_id" to group.id)
                                                    supabase.postgrest.rpc("admin_assign_user", params)
                                                    successCount++
                                                    Log.d("AdminScreens", "RPC update successful for member $memberId")
                                                } catch (rpcError: Exception) {
                                                    Log.e("AdminScreens", "RPC fallback failed for member $memberId", rpcError)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AdminScreens", "Failed to update member $memberId", e)
                                        }
                                    }
                                    Log.d("AdminScreens", "Successfully assigned $successCount new members to group ${group.id}")
                                } catch (e: Exception) {
                                    Log.e("AdminScreens", "Failed to assign new members loop", e)
                                    throw e
                                }
                            } else {
                                Log.d("AdminScreens", "No new members selected for update")
                            }
                            showEditGroupDialog = false
                            fetchAllData()
                        } catch (e: Exception) {
                            Log.e("AdminScreens", "Error saving changes", e)
                            Toast.makeText(context, "Error saving changes: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    if (showDeleteGroupDialog && groupToDelete != null) {
        val group = groupToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = {
                Text(
                    text = "Confirm Deletion",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ErrorColor
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the department '${group.name}'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Black
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                // Unassign all members of this group and set status to rejected
                                supabase.from("profiles").update({
                                    "group_id" to null
                                    "request_status" to "rejected"
                                }) {
                                    filter { eq("group_id", group.id) }
                                }
                                supabase.from("groups").delete { filter { eq("id", group.id) } }
                                Toast.makeText(context, "Department '${group.name}' deleted", Toast.LENGTH_SHORT).show()
                                showDeleteGroupDialog = false
                                fetchAllData()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error deleting group: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorColor,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete Department", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteGroupDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DarkGrey
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun GroupCard(group: Group, onEditClick: (Group) -> Unit, onDeleteClick: (Group) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEditClick(group) },
        colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = group.name, style = MaterialTheme.typography.titleMedium, color = Primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Time: ${group.startTime} - ${group.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkGrey
                    )
                }
                Row {
                    IconButton(
                        onClick = { onEditClick(group) },
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Primary.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Department", tint = Primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onDeleteClick(group) },
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(ErrorColor.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Department", tint = ErrorColor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDialog(
    isEdit: Boolean = false,
    title: String,
    groupName: String, onGroupNameChange: (String) -> Unit,
    lat: String, onLatChange: (String) -> Unit,
    long: String, onLongChange: (String) -> Unit,
    radius: String, onRadiusChange: (String) -> Unit,
    startTime: String, onStartTimeChange: (String) -> Unit,
    endTime: String, onEndTimeChange: (String) -> Unit,
    isLocationRestricted: Boolean, onLocationRestrictedChange: (Boolean) -> Unit, // New Params
    availableUsers: List<Profile>,
    selectedUserIds: Set<String>,
    onUserSelectionChanged: (String, Boolean) -> Unit,
    currentMembers: List<Profile> = emptyList(),
    onRemoveMember: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current // Add this line to get context
    var showRemoveMemberDialog by remember { mutableStateOf(false) }
    var memberToRemoveId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text("Department Name") },
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
                Spacer(modifier = Modifier.height(12.dp))

                // Location Restriction Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Restrict Location",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Black
                    )
                    Switch(
                        checked = isLocationRestricted,
                        onCheckedChange = onLocationRestrictedChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = White,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = DarkGrey,
                            uncheckedTrackColor = LightGrey
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Always show location fields, but make them optional if unrestricted
                OutlinedTextField(
                    value = lat,
                    onValueChange = onLatChange,
                    label = { Text(if (isLocationRestricted) "Latitude *" else "Latitude (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = long,
                    onValueChange = onLongChange,
                    label = { Text(if (isLocationRestricted) "Longitude *" else "Longitude (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = radius,
                    onValueChange = onRadiusChange,
                    label = { Text(if (isLocationRestricted) "Radius (meters) *" else "Radius (meters) (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                Spacer(modifier = Modifier.height(12.dp))

                if (!isLocationRestricted) {
                    Text(
                        text = "Note: Location check will be skipped for this department.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkGrey,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Active Hours",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Black
                    )
                    // Add a help icon with tooltip
                    IconButton(onClick = {
                        // Show tooltip or dialog with help information
                        Toast.makeText(context, "Set the active hours for the department.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = DarkGrey)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = onStartTimeChange,
                        label = { Text("Start Time") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
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
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = onEndTimeChange,
                        label = { Text("End Time") },
                        modifier = Modifier.weight(1f),
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
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (isEdit) {
                    Text(
                        "Current Members:",
                        style = MaterialTheme.typography.titleSmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    currentMembers.forEach { member ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = LightGrey),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    member.name ?: "Unknown",
                                    modifier = Modifier.weight(1f),
                                    color = Black,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(onClick = {
                                    memberToRemoveId = member.id
                                    showRemoveMemberDialog = true
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Member", tint = ErrorColor)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    "Available Members:",
                    style = MaterialTheme.typography.titleSmall,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightGrey),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(200.dp)
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            if (availableUsers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No unassigned users available",
                                        color = DarkGrey,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                availableUsers.forEach { user ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onUserSelectionChanged(user.id, !selectedUserIds.contains(user.id)) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Checkbox(
                                            checked = selectedUserIds.contains(user.id),
                                            onCheckedChange = { isSelected -> onUserSelectionChanged(user.id, isSelected) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Primary,
                                                uncheckedColor = DarkGrey
                                            )
                                        )
                                        Text(
                                            text = user.name ?: "Unknown",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Black,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    if (user != availableUsers.last()) {
                                        HorizontalDivider(color = MediumGrey, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = DarkGrey
                )
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        }
    )

    if (showRemoveMemberDialog && memberToRemoveId != null) {
        AlertDialog(
            onDismissRequest = { showRemoveMemberDialog = false },
            title = {
                Text(
                    text = "Confirm Removal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ErrorColor
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove this member from the department?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Black
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveMember(memberToRemoveId!!)
                        showRemoveMemberDialog = false
                        memberToRemoveId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorColor,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Remove Member", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveMemberDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DarkGrey
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun AdminProfileScreen(navController: NavController) {
    UserProfileScreen(navController)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRequestsScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    var pendingRequests by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var groupsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRequest by remember { mutableStateOf<Profile?>(null) }

    suspend fun fetchData() {
        try {
            isLoading = true
            // Fetch Groups for name mapping
            val groups = supabase.from("groups").select().decodeList<Group>()
            groupsMap = groups.associate { it.id to it.name }

            // Fetch Pending Requests
            val requests = supabase.from("profiles").select {
                filter {
                    eq("request_status", "pending")
                }
            }.decodeList<Profile>()
            pendingRequests = requests
        } catch (e: Exception) {
            Log.e("AdminRequests", "Error fetching requests", e)
            Toast.makeText(context, "Error fetching requests: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Department Requests", color = White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary),
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
                .background(LightGrey)
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Primary)
            } else if (pendingRequests.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "No Requests", tint = Success, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No pending requests", style = MaterialTheme.typography.titleMedium, color = DarkGrey)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pendingRequests) { profile ->
                        RequestCard(
                            profile = profile,
                            groupName = groupsMap[profile.requestedGroupId] ?: "Unknown Department",
                            onClick = { selectedRequest = profile },
                            onApprove = {
                                scope.launch {
                                    try {
                                        val requestedGroupId = profile.requestedGroupId
                                        if (requestedGroupId != null) {
                                            Log.d("AdminRequests", "Approving request for user ${profile.id} to group $requestedGroupId")

                                            // Use the V2 RPC to handle assignment AND status update atomically
                                            try {
                                                val params = mapOf("p_user_id" to profile.id, "p_group_id" to requestedGroupId)
                                                supabase.postgrest.rpc("approve_group_request_v2", params)
                                                Log.d("AdminRequests", "Request approved via RPC V2")
                                                Toast.makeText(context, "Request Approved", Toast.LENGTH_SHORT).show()
                                                fetchData()
                                            } catch (e: Exception) {
                                                Log.e("AdminRequests", "RPC V2 failed", e)
                                                Toast.makeText(context, "Failed to approve: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Error: No requested group ID", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AdminRequests", "Error approving", e)
                                        Toast.makeText(context, "Error approving: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onReject = {
                                scope.launch {
                                    try {
                                        try {
                                            val params = mapOf("p_user_id" to profile.id)
                                            supabase.postgrest.rpc("reject_group_request", params)
                                            Toast.makeText(context, "Request Rejected", Toast.LENGTH_SHORT).show()
                                            fetchData()
                                        } catch (rpcError: Exception) {
                                            Log.e("AdminRequests", "RPC reject_group_request failed", rpcError)
                                            // Fallback: manually update status
                                            try {
                                                supabase.from("profiles").update({
                                                    set("request_status", "rejected")
                                                    set("group_id", null as String?)
                                                    set("requested_group_id", null as String?)
                                                }) {
                                                    filter { eq("id", profile.id) }
                                                }
                                                Toast.makeText(context, "Request Rejected", Toast.LENGTH_SHORT).show()
                                                fetchData()
                                            } catch (e: Exception) {
                                                Log.e("AdminRequests", "Fallback update failed", e)
                                                Toast.makeText(context, "Error rejecting: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AdminRequests", "Error rejecting", e)
                                        Toast.makeText(context, "Error rejecting: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (selectedRequest != null) {
        val profile = selectedRequest!!
        AlertDialog(
            onDismissRequest = { selectedRequest = null },
            title = {
                Text(
                    text = "Employee Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            },
            text = {
                Column {
                    DetailRow(icon = Icons.Default.Person, label = "Name", value = profile.name ?: "N/A")
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(icon = Icons.Default.LocationOn, label = "Seating", value = profile.seating ?: "N/A")
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(icon = Icons.Default.Phone, label = "Phone", value = profile.phone ?: "N/A")
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(icon = Icons.Default.Email, label = "Email", value = profile.email ?: "N/A")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRequest = null }) {
                    Text("Close", color = Primary)
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = DarkGrey)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Black, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RequestCard(profile: Profile, groupName: String, onApprove: () -> Unit, onReject: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = profile.name ?: "Unknown User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Black)
                Text(text = profile.email ?: "", style = MaterialTheme.typography.bodySmall, color = DarkGrey)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Requesting: $groupName", style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Medium)
            }
            Row {
                IconButton(onClick = onApprove, modifier = Modifier.background(Success.copy(alpha = 0.1f), CircleShape)) {
                    Icon(Icons.Default.Check, contentDescription = "Approve", tint = Success)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onReject, modifier = Modifier.background(ErrorColor.copy(alpha = 0.1f), CircleShape)) {
                    Icon(Icons.Default.Close, contentDescription = "Reject", tint = ErrorColor)
                }
            }
        }
    }
}
