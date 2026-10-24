package com.daksha.hrms.ui.screens.talent

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
import com.daksha.hrms.data.api.models.OnboardingTaskDto
import com.daksha.hrms.data.api.models.PositionDto
import com.daksha.hrms.data.api.models.ReviewDto
import com.daksha.hrms.ui.components.StatusBadge
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Teal600

@Composable
fun TalentScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Onboarding", "Reviews", "Recruitment")

    val positions by viewModel.positionsList.collectAsState()
    val onboardingTasks by viewModel.onboardingTasksList.collectAsState()
    val reviews by viewModel.reviewsList.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadTalent()
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
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> OnboardingTabContent(tasks = onboardingTasks)
            1 -> ReviewsTabContent(reviews = reviews)
            2 -> RecruitmentTabContent(positions = positions)
        }
    }
}

@Composable
fun OnboardingTabContent(tasks: List<OnboardingTaskDto>) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No onboarding tasks.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
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
                            Text(task.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            Text("Due: ${task.dueOn} • ${task.employee?.name ?: ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                        }
                        StatusBadge(status = if (task.completedAt != null) "COMPLETED" else "PENDING")
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewsTabContent(reviews: List<ReviewDto>) {
    if (reviews.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No performance reviews.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(reviews, key = { it.id }) { rev ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(rev.employee?.name ?: "Employee", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            StatusBadge(status = rev.status)
                        }
                        Text("Cycle: ${rev.cycle}", fontSize = 12.sp, color = Teal600)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Goals: ${rev.goals}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                        if (!rev.feedback.isNullOrBlank()) {
                            Text("Feedback: ${rev.feedback}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecruitmentTabContent(positions: List<PositionDto>) {
    if (positions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No open recruitment positions.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(positions, key = { it.id }) { pos ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(pos.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            StatusBadge(status = pos.status)
                        }
                        Text("${pos.department} • ${pos.openings} opening(s)", fontSize = 12.sp, color = Teal600)
                        Text("Candidates: ${pos.candidates.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
