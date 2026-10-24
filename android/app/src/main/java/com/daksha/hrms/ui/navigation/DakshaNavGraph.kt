package com.daksha.hrms.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daksha.hrms.ui.components.AppBottomNav
import com.daksha.hrms.ui.components.AppTopBar
import com.daksha.hrms.ui.components.LoadingOverlay
import com.daksha.hrms.ui.components.Screen
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.screens.admin.AdminScreen
import com.daksha.hrms.ui.screens.attendance.AttendanceScreen
import com.daksha.hrms.ui.screens.auth.AuthViewModel
import com.daksha.hrms.ui.screens.auth.ForgotPasswordScreen
import com.daksha.hrms.ui.screens.auth.LoginScreen
import com.daksha.hrms.ui.screens.auth.RequestAccessScreen
import com.daksha.hrms.ui.screens.dashboard.DashboardScreen
import com.daksha.hrms.ui.screens.employees.EmployeeListScreen
import com.daksha.hrms.ui.screens.payroll.PayrollScreen
import com.daksha.hrms.ui.screens.profile.ProfileScreen
import com.daksha.hrms.ui.screens.requests.RequestsScreen
import com.daksha.hrms.ui.screens.talent.TalentScreen

@Composable
fun DakshaNavGraph(
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController = rememberNavController()
) {
    val user by mainViewModel.user.collectAsState()
    val activeCompanyId by mainViewModel.activeCompanyId.collectAsState()
    val loading by mainViewModel.loading.collectAsState()
    val error by mainViewModel.error.collectAsState()
    val notice by mainViewModel.notice.collectAsState()
    val requests by mainViewModel.requestsList.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.clearError()
        }
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.clearNotice()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "login"

    val isAuthRoute = currentRoute in listOf("login", "request_access", "forgot_password")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isAuthRoute) {
                AppTopBar(
                    user = user,
                    activeCompanyId = activeCompanyId,
                    onCompanySelected = { mainViewModel.setCompany(it) },
                    onChangePassword = { navController.navigate("profile") },
                    onSignOut = {
                        mainViewModel.signOut {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isAuthRoute) {
                val pendingCount = requests.count { it.status == "PENDING" }
                AppBottomNav(
                    currentRoute = currentRoute,
                    role = mainViewModel.userRole,
                    pendingRequestsCount = pendingCount,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("overview") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        mainViewModel.initMe { }
                        navController.navigate("overview") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onRequestAccess = { navController.navigate("request_access") },
                    onForgotPassword = { navController.navigate("forgot_password") }
                )
            }

            composable("request_access") {
                RequestAccessScreen(
                    viewModel = authViewModel,
                    onBackToLogin = { navController.popBackStack() }
                )
            }

            composable("forgot_password") {
                ForgotPasswordScreen(
                    viewModel = authViewModel,
                    onBackToLogin = { navController.popBackStack() }
                )
            }

            composable("overview") {
                DashboardScreen(
                    viewModel = mainViewModel,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            composable("people") {
                EmployeeListScreen(viewModel = mainViewModel)
            }

            composable("attendance") {
                AttendanceScreen(viewModel = mainViewModel)
            }

            composable("requests") {
                RequestsScreen(viewModel = mainViewModel)
            }

            composable("payroll") {
                PayrollScreen(viewModel = mainViewModel)
            }

            composable("talent") {
                TalentScreen(viewModel = mainViewModel)
            }

            composable("admin") {
                AdminScreen(viewModel = mainViewModel)
            }

            composable("profile") {
                ProfileScreen(mainViewModel = mainViewModel, authViewModel = authViewModel)
            }
        }

        if (loading) {
            LoadingOverlay()
        }
    }
}
