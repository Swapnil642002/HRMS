package com.daksha.hrms.ui.screens.attendance

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.daksha.hrms.data.api.models.AttendanceDto
import com.daksha.hrms.ui.components.StatusBadge
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Slate900
import com.daksha.hrms.ui.theme.Teal600
import com.daksha.hrms.util.BiometricHelper
import com.daksha.hrms.util.LocationHelper
import kotlinx.coroutines.launch

@Composable
fun AttendanceScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val attendanceList by viewModel.attendanceList.collectAsState()
    val securityData by viewModel.securityData.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val error by viewModel.error.collectAsState()

    var pendingPunchDirection by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && pendingPunchDirection != null) {
            val direction = pendingPunchDirection!!
            pendingPunchDirection = null
            executeGpsPunch(context, viewModel, direction)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchAttendanceSecurity()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Attendance Punch Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Teal600)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GPS Geofenced Punch",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                val branch = securityData?.branch
                if (branch != null) {
                    Text(
                        text = "Assigned Branch: ${branch.name}, ${branch.city} (50m radius)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "Branch assignment required before punching attendance.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null && BiometricHelper.isBiometricAvailable(activity)) {
                                BiometricHelper.promptBiometric(
                                    activity = activity,
                                    title = "Check In Verification",
                                    subtitle = "Confirm location and check in",
                                    onSuccess = {
                                        triggerGpsAndPunch(context, locationPermissionLauncher, viewModel, "IN") { pendingPunchDirection = "IN" }
                                    },
                                    onError = { /* handled in helper */ }
                                )
                            } else {
                                triggerGpsAndPunch(context, locationPermissionLauncher, viewModel, "IN") { pendingPunchDirection = "IN" }
                            }
                        },
                        enabled = branch != null && !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Teal600),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check In", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null && BiometricHelper.isBiometricAvailable(activity)) {
                                BiometricHelper.promptBiometric(
                                    activity = activity,
                                    title = "Check Out Verification",
                                    subtitle = "Confirm location and check out",
                                    onSuccess = {
                                        triggerGpsAndPunch(context, locationPermissionLauncher, viewModel, "OUT") { pendingPunchDirection = "OUT" }
                                    },
                                    onError = { /* handled in helper */ }
                                )
                            } else {
                                triggerGpsAndPunch(context, locationPermissionLauncher, viewModel, "OUT") { pendingPunchDirection = "OUT" }
                            }
                        },
                        enabled = branch != null && !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Out", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Attendance Records",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (attendanceList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No attendance records found.",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attendanceList, key = { it.id }) { record ->
                    AttendanceCard(record = record)
                }
            }
        }
    }
}

private fun triggerGpsAndPunch(
    context: android.content.Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    viewModel: MainViewModel,
    direction: String,
    onPermissionRequired: () -> Unit
) {
    val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    if (fineLocation == PackageManager.PERMISSION_GRANTED) {
        executeGpsPunch(context, viewModel, direction)
    } else {
        onPermissionRequired()
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
}

private fun executeGpsPunch(
    context: android.content.Context,
    viewModel: MainViewModel,
    direction: String
) {
    kotlinx.coroutines.MainScope().launch {
        val result = LocationHelper.getCurrentLocation(context)
        result.onSuccess { loc ->
            viewModel.punchAttendance(direction, loc.latitude, loc.longitude, loc.accuracyMetres, loc.capturedAt) {
                // punched successfully
            }
        }.onFailure {
            viewModel.punchAttendance(direction, 0.0, 0.0, 0.0, System.currentTimeMillis()) { }
        }
    }
}

@Composable
fun AttendanceCard(record: AttendanceDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = record.employee?.name ?: "Employee",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "Date: ${record.day}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
                Text(
                    text = "In: ${record.checkIn.takeLast(8)} • Out: ${record.checkOut?.takeLast(8) ?: "Working"}",
                    fontSize = 13.sp,
                    color = Teal600,
                    fontWeight = FontWeight.Medium
                )
            }
            StatusBadge(status = record.source)
        }
    }
}
