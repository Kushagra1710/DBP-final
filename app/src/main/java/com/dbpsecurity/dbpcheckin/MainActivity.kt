package com.dbpsecurity.dbpcheckin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.dbpsecurity.dbpcheckin.screens.*
import com.dbpsecurity.dbpcheckin.ui.theme.DBPCheckinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DBPCheckinTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") { SplashScreen(navController) }
                        composable("signin") { SignInScreen(navController) }
                        composable("signup") { SignUpScreen(navController) }
                        composable("admin_home") { AdminHomeScreen(navController) }
                        composable("admin_management") { AdminManagementScreen(navController) }
                        composable("admin_profile") { AdminProfileScreen(navController) }
                        composable("admin_requests") { AdminRequestsScreen(navController) }
                        composable("admin_employee_management") { AdminEmployeeScreen(navController) }
                        composable("admin_download_attendance") { AdminDownloadAttendanceScreen(navController) }
                        composable("employee_home") { EmployeeHomeScreen(navController) }
                        composable("employee_profile") { EmployeeProfileScreen(navController) }
                         composable(
                             "face_detection?actionType={actionType}&shiftName={shiftName}",
                             arguments = listOf(
                                 navArgument("actionType") {
                                     type = NavType.StringType
                                     defaultValue = "CHECK_IN"
                                 },
                                 navArgument("shiftName") {
                                     type = NavType.StringType
                                     nullable = true
                                     defaultValue = null
                                 }
                             )
                         ) { backStackEntry ->
                             val actionType = backStackEntry.arguments?.getString("actionType") ?: "CHECK_IN"
                             val shiftName = backStackEntry.arguments?.getString("shiftName")
                             FaceDetectionScreen(navController, actionType, shiftName)
                         }
                        composable("block_coordinator") { BlockCoordinatorScreen(navController) }
                        composable("block_attendance_dashboard") { BlockAttendanceDashboardScreen(navController) }
                    }
                }
            }
        }
    }
}
