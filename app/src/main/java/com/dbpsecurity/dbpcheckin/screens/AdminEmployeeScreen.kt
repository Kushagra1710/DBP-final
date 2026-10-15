package com.dbpsecurity.dbpcheckin.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow // Added import
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.*
import com.dbpsecurity.dbpcheckin.ui.theme.Error as ErrorColor
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEmployeeScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String>("All") } // "All", "Assigned", "Unassigned", or GroupID
    var expandedFilter by remember { mutableStateOf(false) }

    var allProfiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var allGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var userToDelete by remember { mutableStateOf<Profile?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var userToView by remember { mutableStateOf<Profile?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }

    // Fetch Data
    LaunchedEffect(Unit) {
        try {
            val groups = withContext(Dispatchers.IO) {
                supabase.from("groups").select().decodeList<Group>()
            }
            val profiles = withContext(Dispatchers.IO) {
                supabase.from("profiles").select {
                    filter { eq("role", "employee") }
                }.decodeList<Profile>()
            }
            allGroups = groups
            allProfiles = profiles
            isLoading = false
        } catch (e: Exception) {
            Log.e("AdminEmployeeScreen", "Error fetching data", e)
            Toast.makeText(context, "Error loading data", Toast.LENGTH_SHORT).show()
            isLoading = false
        }
    }

    // Filter Logic
    val filteredProfiles by remember {
        derivedStateOf {
            allProfiles.filter { profile ->
                // 1. Search Filter
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    val groupName = allGroups.find { it.id == profile.groupId }?.name.orEmpty()
                    (profile.name ?: "").contains(searchQuery, ignoreCase = true) ||
                    (profile.email ?: "").contains(searchQuery, ignoreCase = true) ||
                    (profile.phone ?: "").contains(searchQuery, ignoreCase = true) || // FIX: Safe Access
                    groupName.contains(searchQuery, ignoreCase = true) // FIX: Safe Access
                }

                // 2. Dropdown Filter
                val matchesType = when (selectedFilter) {
                    "All" -> true
                    "Assigned" -> profile.groupId != null
                    "Unassigned" -> profile.groupId == null
                    else -> profile.groupId == selectedFilter // Group UUID
                }

                matchesSearch && matchesType
            }.sortedBy { it.name }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Employees", color = White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5)) // Light Grey background
                .padding(16.dp)
        ) {
            // --- Search & Filter Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Bar
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search (Name, Dept...)", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Primary) },
                    modifier = Modifier
                        .weight(1f)
                        .background(White, RoundedCornerShape(12.dp)),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Primary,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = White,
                        unfocusedContainerColor = White
                    )
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Filter Button
                Box {
                    Surface(
                        onClick = { expandedFilter = true },
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = White,
                        shadowElevation = 4.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
                    ) {
                         Box(contentAlignment = Alignment.Center) {
                             Icon(Icons.Default.Tune, contentDescription = "Filter", tint = Primary)
                         }
                    }

                    DropdownMenu(
                        expanded = expandedFilter,
                        onDismissRequest = { expandedFilter = false },
                        modifier = Modifier.background(White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Employees", color = Color.Black) },
                            onClick = { selectedFilter = "All"; expandedFilter = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Assigned Only", color = Color.Black) },
                            onClick = { selectedFilter = "Assigned"; expandedFilter = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Unassigned Only", color = Color.Black) },
                            onClick = { selectedFilter = "Unassigned"; expandedFilter = false }
                        )
                        HorizontalDivider()
                        allGroups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text("Dept: ${group.name}", color = Color.Black) },
                                onClick = { selectedFilter = group.id; expandedFilter = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Employee List ---
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                Text(
                    text = "Showing ${filteredProfiles.size} Employees",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.DarkGray, // Darker label
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredProfiles) { profile ->
                        val groupName = allGroups.find { it.id == profile.groupId }?.name ?: "Unassigned"
                        EmployeeCard(
                            profile = profile,
                            groupName = groupName,
                            onClick = {
                                userToView = profile
                                showDetailDialog = true
                            },
                            onDelete = {
                                userToDelete = profile
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Delete Confirmation Dialog ---
    if (showDeleteDialog && userToDelete != null) {
        val user = userToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Employee?", color = ErrorColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Are you sure you want to PERMANENTLY delete:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(user.name ?: "Unknown Name", fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(user.email ?: "No Email", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This will remove them from all departments, attendance logs, and reports. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    supabase.from("profiles").delete {
                                        filter { eq("id", user.id) }
                                    }
                                }
                                Toast.makeText(context, "${user.name} deleted successfully", Toast.LENGTH_SHORT).show()
                                
                                // Clean up local list
                                allProfiles = allProfiles.filter { it.id != user.id }
                                showDeleteDialog = false
                                userToDelete = null
                            } catch (e: Exception) {
                                Log.e("AdminEmployeeScreen", "Delete failed", e)
                                Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Detail View Dialog ---
    if (showDetailDialog && userToView != null) {
        val user = userToView!!
        val group = allGroups.find { it.id == user.groupId }
        
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = {
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Primary)
                     Spacer(modifier = Modifier.width(8.dp))
                     Text("Employee Details", color = Color.Black)
                 }
            },
            containerColor = White,
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // FIX: Renamed to EmployeeDetailRow
                    EmployeeDetailRow(Icons.Default.Person, "Name", user.name ?: "N/A")
                    EmployeeDetailRow(Icons.Default.Email, "Email", user.email ?: "N/A")
                    EmployeeDetailRow(Icons.Default.Phone, "Phone", user.phone ?: "N/A") // FIX: Safe Access
                    EmployeeDetailRow(Icons.Default.Home, "Department", group?.name ?: "Unassigned")
                    // Seating / Tehsil
                    EmployeeDetailRow(Icons.Default.LocationOn, "Seating/Location", user.seating ?: "Not Set") // FIX: Safe Access
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("ID: ${user.id}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// FIX: Renamed Function and Fix Text Params
@Composable
fun EmployeeDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Primary.copy(alpha=0.7f), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color.Black)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun EmployeeCard(
    profile: Profile,
    groupName: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isAssigned = (groupName != "Unassigned")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(16.dp)), // Soft shadow
        elevation = CardDefaults.cardElevation(0.dp), // Handled by shadow
        colors = CardDefaults.cardColors(containerColor = White),
        shape = RoundedCornerShape(16.dp),
        border = if (isAssigned) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Placeholder
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = if (isAssigned) Primary.copy(alpha = 0.1f) else Color.Gray.copy(alpha=0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (profile.name ?: "?").take(1).uppercase(),
                        color = if (isAssigned) Primary else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name ?: "Unknown", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Department Badge
                Surface(
                    color = if (isAssigned) Primary.copy(alpha=0.08f) else Color.Gray.copy(alpha=0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAssigned) Primary else Color.DarkGray,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Actions
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ErrorColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}
