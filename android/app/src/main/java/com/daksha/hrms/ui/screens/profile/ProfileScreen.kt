package com.daksha.hrms.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.screens.auth.AuthViewModel
import com.daksha.hrms.ui.theme.Slate800
import com.daksha.hrms.ui.theme.Teal600

@Composable
fun ProfileScreen(
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel
) {
    val user by mainViewModel.user.collectAsState()
    val error by authViewModel.error.collectAsState()
    val message by authViewModel.message.collectAsState()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "User Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Name: ${user?.name ?: "N/A"}", color = MaterialTheme.colorScheme.onPrimary)
                Text("Email: ${user?.email ?: "N/A"}", color = MaterialTheme.colorScheme.onPrimary)
                Text("Role: ${mainViewModel.userRole}", color = Teal600, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Change Password",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                if (!error.isNullOrBlank()) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                if (!message.isNullOrBlank()) {
                    Text(message!!, color = Teal600, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password (min 14 chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        authViewModel.changePassword(currentPassword, newPassword) {
                            currentPassword = ""
                            newPassword = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal600),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Password", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
