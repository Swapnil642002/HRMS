package com.daksha.hrms.ui.screens.payroll

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daksha.hrms.data.api.models.PayrollLineDto
import com.daksha.hrms.data.api.models.PayrollRunDto
import com.daksha.hrms.ui.components.StatusBadge
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Teal600

@Composable
fun PayrollScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val payrollRuns by viewModel.payrollRunsList.collectAsState()
    val role = viewModel.userRole
    val isFinance = role == "ADMIN" || role == "FINANCE"

    var showCalculateModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadPayroll()
    }

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
                text = "Payroll Workspace",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )

            if (isFinance) {
                Button(
                    onClick = { showCalculateModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal600)
                ) {
                    Text("Calculate Draft")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (payrollRuns.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No payroll runs calculated yet.",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(payrollRuns, key = { it.id }) { run ->
                    PayrollRunCard(
                        run = run,
                        isFinance = isFinance,
                        onFinalize = { viewModel.finalizePayrollRun(run.id) },
                        onDownloadPayslip = { lineId -> viewModel.downloadPayslip(context, lineId) }
                    )
                }
            }
        }
    }

    if (showCalculateModal) {
        CalculatePayrollModal(
            onCalculate = { month ->
                viewModel.calculatePayrollRun(month)
                showCalculateModal = false
            },
            onDismiss = { showCalculateModal = false }
        )
    }
}

@Composable
fun PayrollRunCard(
    run: PayrollRunDto,
    isFinance: Boolean,
    onFinalize: () -> Unit,
    onDownloadPayslip: (String) -> Unit
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
                Text(
                    text = "Month: ${run.month}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                StatusBadge(status = run.status)
            }

            if (run.status == "DRAFT" && isFinance) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onFinalize,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal600)
                ) {
                    Text("Finalize Payroll Run")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Salary Lines (${run.lines.size}):",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Teal600
            )

            run.lines.forEach { line ->
                Spacer(modifier = Modifier.height(6.dp))
                PayrollLineRow(line = line, onDownloadPayslip = onDownloadPayslip)
                Divider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
fun PayrollLineRow(
    line: PayrollLineDto,
    onDownloadPayslip: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = line.employee?.name ?: "Employee",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "Net: INR ${(line.net / 100.0)}",
                fontSize = 12.sp,
                color = Teal600,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(onClick = { onDownloadPayslip(line.id) }) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = "Download Payslip", tint = Teal600)
        }
    }
}

@Composable
fun CalculatePayrollModal(
    onCalculate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var month by remember { mutableStateOf("2026-10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculate Payroll Draft") },
        text = {
            OutlinedTextField(
                value = month,
                onValueChange = { month = it },
                label = { Text("Month (YYYY-MM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onCalculate(month) },
                colors = ButtonDefaults.buttonColors(containerColor = Teal600)
            ) {
                Text("Calculate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
