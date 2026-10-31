package com.daksha.hrms

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.daksha.hrms.data.repository.*
import com.daksha.hrms.ui.navigation.DakshaNavGraph
import com.daksha.hrms.ui.screens.MainViewModel
import com.daksha.hrms.ui.screens.auth.AuthViewModel
import com.daksha.hrms.ui.theme.DakshaTheme

class MainActivity : FragmentActivity() {

    private lateinit var authViewModel: AuthViewModel
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DakshaApplication
        val net = app.networkModule
        val prefs = app.securePreferences

        val authRepository = AuthRepository(net.apiService, prefs, net.cookieJar)
        val employeeRepository = EmployeeRepository(net.apiService)
        val attendanceRepository = AttendanceRepository(net.apiService)
        val requestRepository = RequestRepository(net.apiService)
        val payrollRepository = PayrollRepository(net.apiService)
        val talentRepository = TalentRepository(net.apiService)

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                        AuthViewModel(authRepository) as T
                    modelClass.isAssignableFrom(MainViewModel::class.java) ->
                        MainViewModel(
                            authRepository,
                            employeeRepository,
                            attendanceRepository,
                            requestRepository,
                            payrollRepository,
                            talentRepository,
                            prefs
                        ) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }

        authViewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]
        mainViewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            DakshaTheme {
                DakshaNavGraph(
                    mainViewModel = mainViewModel,
                    authViewModel = authViewModel
                )
            }
        }
    }
}
