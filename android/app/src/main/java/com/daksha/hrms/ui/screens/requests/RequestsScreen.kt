package com.daksha.hrms.ui.screens.requests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daksha.hrms.data.api.models.CreateWorkRequestInput
import com.daksha.hrms.data.api.models.WorkRequestDto
import com.daksha.hrms.ui.components.StatusBadge
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Teal600

@Composable
fun RequestsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val requests by viewModel.requestsList.collectAsState()
    val employees by viewModel.employeesList.collectAsState()
    val role = viewModel.userRole
    val isFinance = role == "ADMIN" || role == "FINANCE"

    var showCreateModal by remember { mutableStateOf(false) }
    var reviewModalRequest by remember { mutableStateOf<WorkRequestDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Requests & Approvals",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )

            Button(
                onClick = { showCreateModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = Teal600)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Request")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (requests.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No work requests found.",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(requests, key = { it.id }) { req ->
                    WorkRequestCard(
                        request = req,
                        isFinance = isFinance,
                        onReview = { reviewModalRequest = req },
                        onPay = { viewModel.payRequest(req.id) },
                        onDownloadTourBill = { viewModel.downloadTourBill(context, req.id) }
                    )
                }
            }
        }
    }

    if (showCreateModal) {
        CreateRequestModal(
            viewModel = viewModel,
            onDismiss = { showCreateModal = false }
        )
    }

    if (reviewModalRequest != null) {
        ReviewRequestModal(
            request = reviewModalRequest!!,
            onDecide = { decision, note ->
                viewModel.decideRequest(reviewModalRequest!!.id, decision, note)
                reviewModalRequest = null
            },
            onDismiss = { reviewModalRequest = null }
        )
    }
}

@Composable
fun WorkRequestCard(
    request: WorkRequestDto,
    isFinance: Boolean,
    onReview: () -> Unit,
    onPay: () -> Unit,
    onDownloadTourBill: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "${request.kind} • ${request.employee?.name ?: "Employee"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                }
                StatusBadge(status = request.status)
            }

            if (request.amount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Amount: INR ${(request.amount / 100.0)}",
                    fontWeight = FontWeight.Bold,
                    color = Teal600,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (request.canReview) {
                    Button(
                        onClick = onReview,
                        colors = ButtonDefaults.buttonColors(containerColor = Teal600)
                    ) {
                        Text("Review")
                    }
                }

                if (request.status == "APPROVED" && isFinance && (request.kind == "EXPENSE" || request.kind == "ADVANCE" || request.kind == "LOAN")) {
                    OutlinedButton(onClick = onPay) {
                        Text("Mark Paid", color = Teal600)
                    }
                }

                if (request.canDownloadTour) {
                    IconButton(onClick = onDownloadTourBill) {
                        Icon(Icons.Default.Description, contentDescription = "Tour Bill PDF", tint = Teal600)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateRequestModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val employees by viewModel.employeesList.collectAsState()
    val currentEmpId = viewModel.currentEmployeeId ?: employees.firstOrNull()?.id ?: ""

    var kind by remember { mutableStateOf("LEAVE") }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf("2026-10-24") }
    var toDate by remember { mutableStateOf("2026-10-25") }
    var detailsNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Work Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("LEAVE", "TRAVEL", "EXPENSE", "ADVANCE", "LOAN").forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k, fontSize = 10.sp) }
                        )
                    }
                }

                if (kind == "LEAVE" || kind == "TRAVEL") {
                    OutlinedTextField(
                        value = fromDate,
                        onValueChange = { fromDate = it },
                        label = { Text("From Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = toDate,
                        onValueChange = { toDate = it },
                        label = { Text("To Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (kind != "LEAVE") {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (INR)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = detailsNote,
                    onValueChange = { detailsNote = it },
                    label = { Text("Reason / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountInCents = amountText.toDoubleOrNull()?.let { (it * 100).toInt() }
                    val detailsMap = mutableMapOf<String, Any>()
                    if (kind == "LEAVE" || kind == "TRAVEL") {
                        detailsMap["from"] = fromDate
                        detailsMap["to"] = toDate
                    }
                    detailsMap["reason"] = detailsNote

                    val input = CreateWorkRequestInput(
                        kind = kind,
                        employeeId = currentEmpId,
                        title = title,
                        details = detailsMap,
                        amount = amountInCents
                    )

                    viewModel.createWorkRequest(input) { onDismiss() }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal600)
            ) {
                Text("Submit Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ReviewRequestModal(
    request: WorkRequestDto,
    onDecide: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review Request: ${request.title}") },
        text = {
            Column {
                Text("Employee: ${request.employee?.name ?: "N/A"}")
                Text("Type: ${request.kind}")
                if (request.amount > 0) {
                    Text("Amount: INR ${(request.amount / 100.0)}")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Decision Note (min 3 chars)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onDecide("APPROVE", note.ifBlank { "Approved" }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal600)
                ) {
                    Text("Approve")
                }
                Button(
                    onClick = { onDecide("REJECT", note.ifBlank { "Rejected" }) },
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
