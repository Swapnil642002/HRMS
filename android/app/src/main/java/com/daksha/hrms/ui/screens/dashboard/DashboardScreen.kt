package com.daksha.hrms.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Teal600

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val dashboardData by viewModel.dashboardData.collectAsState()
    val user by viewModel.user.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Welcome Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "CONNECTED PEOPLE. CLEARER DECISIONS.",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Teal600,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Welcome back, ${user?.name ?: "Team Member"}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keep your team records, requests, and daily operations in one place.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onNavigate("requests") },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal600),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open Requests ↗")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Row
        val stats = dashboardData
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Team Members",
                value = stats?.employees?.toString() ?: "0",
                subtitle = "Active employees",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Checked In",
                value = stats?.present?.toString() ?: "0",
                subtitle = "Today's punches",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Pending",
                value = stats?.pending?.toString() ?: "0",
                subtitle = "Approval requests",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Department Distribution
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Team Distribution",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                val departments = stats?.departments ?: emptyList()
                if (departments.isEmpty()) {
                    Text(
                        text = "No department data available yet.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                } else {
                    departments.forEach { dept ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dept.name, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                            Text("${dept.count} people", color = Teal600, fontWeight = FontWeight.Bold)
                        }
                        Divider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Quick Actions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onNavigate("attendance") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Time & Attendance Verification", color = Teal600)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onNavigate("payroll") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Payslips & Payroll Workspace", color = Teal600)
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Teal600)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
        }
    }
}
