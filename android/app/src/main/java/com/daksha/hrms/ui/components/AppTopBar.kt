package com.daksha.hrms.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daksha.hrms.BuildConfig
import com.daksha.hrms.data.api.models.MembershipDto
import com.daksha.hrms.data.api.models.UserResponse
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Teal600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    user: UserResponse?,
    activeCompanyId: String?,
    onCompanySelected: (String) -> Unit,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit
) {
    var expandedCompanyDropdown by remember { mutableStateOf(false) }

    val activeMembership = user?.memberships?.find { it.company.id == activeCompanyId }
        ?: user?.memberships?.firstOrNull()

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Slate800,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DAKSHA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Teal600
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (BuildConfig.FLAVOR == "dev") {
                        Text(
                            text = "[DEV]",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = activeMembership?.company?.name ?: "Micromap HRMS",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }
        },
        actions = {
            if (user != null && user.memberships.size > 1) {
                IconButton(onClick = { expandedCompanyDropdown = true }) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = "Switch Company",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                DropdownMenu(
                    expanded = expandedCompanyDropdown,
                    onDismissRequest = { expandedCompanyDropdown = false }
                ) {
                    user.memberships.forEach { membership ->
                        DropdownMenuItem(
                            text = { Text(membership.company.name) },
                            onClick = {
                                expandedCompanyDropdown = false
                                onCompanySelected(membership.company.id)
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onChangePassword) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Change Password",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Sign Out",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    )
}
