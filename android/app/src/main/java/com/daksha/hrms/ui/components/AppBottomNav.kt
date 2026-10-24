package com.daksha.hrms.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Teal600

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Overview : Screen("overview", "Overview", Icons.Default.Dashboard)
    object People : Screen("people", "People", Icons.Default.People)
    object Attendance : Screen("attendance", "Attendance", Icons.Default.AccessTime)
    object Requests : Screen("requests", "Requests", Icons.Default.SwapHoriz)
    object Payroll : Screen("payroll", "Payroll", Icons.Default.Receipt)
    object Talent : Screen("talent", "Talent", Icons.Default.Work)
    object Admin : Screen("admin", "Admin", Icons.Default.Settings)
}

@Composable
fun AppBottomNav(
    currentRoute: String,
    role: String,
    pendingRequestsCount: Int,
    onNavigate: (String) -> Unit
) {
    val items = mutableListOf<Screen>(
        Screen.Overview,
        Screen.People,
        Screen.Attendance,
        Screen.Requests,
        Screen.Payroll
    )

    if (role == "ADMIN" || role == "HR") {
        items.add(Screen.Talent)
    }
    if (role == "ADMIN") {
        items.add(Screen.Admin)
    }

    NavigationBar(
        containerColor = Slate800,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        items.forEach { screen ->
            val isSelected = currentRoute == screen.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen.route) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (screen == Screen.Requests && pendingRequestsCount > 0) {
                                Badge { Text(pendingRequestsCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title
                        )
                    }
                },
                label = { Text(screen.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Slate800,
                    selectedTextColor = Teal600,
                    indicatorColor = Teal600,
                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
            )
        }
    }
}
