package com.daksha.hrms.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daksha.hrms.data.api.models.AccessRequestDto
import com.daksha.hrms.data.api.models.AuditDto
import com.daksha.hrms.ui.components.StatusBadge
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Teal600

@Composable
fun AdminScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val accessRequests by viewModel.accessRequestsList.collectAsState()
    val auditLogs by viewModel.auditLogsList.collectAsState()

    var reviewModalRequest by remember { mutableStateOf<AccessRequestDto?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadAdmin()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Slate800,
            contentColor = Teal600
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Access Requests", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Audit Trail", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            if (accessRequests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pending access requests.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accessRequests, key = { it.id }) { req ->
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(req.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    Text("${req.employeeCode} • ${req.email}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                                }
                                if (req.status == "PENDING") {
                                    Button(
                                        onClick = { reviewModalRequest = req },
                                        colors = ButtonDefaults.buttonColors(containerColor = Teal600)
                                    ) {
                                        Text("Review")
                                    }
                                } else {
                                    StatusBadge(status = req.status)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (auditLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No audit log entries.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(auditLogs, key = { it.id }) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate800),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(log.action, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Teal600)
                                Text("Entity: ${log.entityId} • Actor: ${log.actorId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                                Text("Date: ${log.createdAt}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (reviewModalRequest != null) {
        ReviewAccessRequestModal(
            request = reviewModalRequest!!,
            onDecide = { decision, note ->
                viewModel.decideAccessRequest(reviewModalRequest!!.id, decision, note)
                reviewModalRequest = null
            },
            onDismiss = { reviewModalRequest = null }
        )
    }
}

@Composable
fun ReviewAccessRequestModal(
    request: AccessRequestDto,
    onDecide: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review Access Request: ${request.name}") },
        text = {
            Column {
                Text("Code: ${request.employeeCode}")
                Text("Email: ${request.email}")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Review Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onDecide("APPROVE", note) },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal600)
                ) {
                    Text("Approve")
                }
                Button(
                    onClick = { onDecide("REJECT", note) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reject")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
