package com.daksha.hrms.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daksha.hrms.data.api.models.*
import com.daksha.hrms.data.local.SecurePreferences
import com.daksha.hrms.data.repository.*
import com.daksha.hrms.util.PdfHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository,
    private val employeeRepository: EmployeeRepository,
    private val attendanceRepository: AttendanceRepository,
    private val requestRepository: RequestRepository,
    private val payrollRepository: PayrollRepository,
    private val talentRepository: TalentRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _user = MutableStateFlow<UserResponse?>(null)
    val user: StateFlow<UserResponse?> = _user.asStateFlow()

    private val _activeCompanyId = MutableStateFlow<String?>(securePreferences.activeCompanyId)
    val activeCompanyId: StateFlow<String?> = _activeCompanyId.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    // Data States
    val dashboardData = MutableStateFlow<DashboardResponse?>(null)
    val employeesList = MutableStateFlow<List<EmployeeDto>>(emptyList())
    val attendanceList = MutableStateFlow<List<AttendanceDto>>(emptyList())
    val securityData = MutableStateFlow<SecurityResponseDto?>(null)
    val branchesList = MutableStateFlow<List<BranchDto>>(emptyList())
    val credentialsList = MutableStateFlow<List<CredentialDto>>(emptyList())
    val requestsList = MutableStateFlow<List<WorkRequestDto>>(emptyList())
    val managersList = MutableStateFlow<List<WorkRequestManagerDto>>(emptyList())
    val payrollRunsList = MutableStateFlow<List<PayrollRunDto>>(emptyList())
    val payrollPoliciesList = MutableStateFlow<List<PayrollPolicyDto>>(emptyList())
    val positionsList = MutableStateFlow<List<PositionDto>>(emptyList())
    val onboardingTasksList = MutableStateFlow<List<OnboardingTaskDto>>(emptyList())
    val reviewsList = MutableStateFlow<List<ReviewDto>>(emptyList())
    val accessRequestsList = MutableStateFlow<List<AccessRequestDto>>(emptyList())
    val auditLogsList = MutableStateFlow<List<AuditDto>>(emptyList())

    val userRole: String
        get() {
            val cid = _activeCompanyId.value
            val m = _user.value?.memberships?.find { it.company.id == cid }
                ?: _user.value?.memberships?.firstOrNull()
            return m?.role ?: "EMPLOYEE"
        }

    val currentEmployeeId: String?
        get() {
            val cid = _activeCompanyId.value
            val m = _user.value?.memberships?.find { it.company.id == cid }
                ?: _user.value?.memberships?.firstOrNull()
            return m?.employeeId
        }

    fun initMe(onUnauthenticated: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            val result = authRepository.getMe()
            _loading.value = false
            result.onSuccess { u ->
                _user.value = u
                if (_activeCompanyId.value == null && u.memberships.isNotEmpty()) {
                    setCompany(u.memberships.first().company.id)
                } else {
                    refreshAllData()
                }
            }.onFailure {
                onUnauthenticated()
            }
        }
    }

    fun setCompany(companyId: String) {
        securePreferences.activeCompanyId = companyId
        _activeCompanyId.value = companyId
        refreshAllData()
    }

    fun refreshAllData() {
        if (_activeCompanyId.value.isNullOrBlank()) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            // Parallel fetches
            val dashRes = employeeRepository.getDashboard()
            val empRes = employeeRepository.getEmployees()
            val reqRes = requestRepository.getRequests()
            val attRes = attendanceRepository.getAttendance()

            _loading.value = false

            dashRes.onSuccess { dashboardData.value = it }
            empRes.onSuccess { employeesList.value = it }
            reqRes.onSuccess { requestsList.value = it }
            attRes.onSuccess { attendanceList.value = it }
        }
    }

    // Attendance Actions
    fun fetchAttendanceSecurity() {
        viewModelScope.launch {
            val res = attendanceRepository.getSecurity()
            res.onSuccess { securityData.value = it }
            if (userRole == "ADMIN" || userRole == "HR") {
                attendanceRepository.getBranches().onSuccess { branchesList.value = it }
                attendanceRepository.getCredentials().onSuccess { credentialsList.value = it }
            }
        }
    }

    fun punchAttendance(
        direction: String,
        lat: Double,
        lng: Double,
        accuracy: Double,
        capturedAt: Long,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val optRes = attendanceRepository.punchOptions(direction, lat, lng, accuracy, capturedAt)
            optRes.onSuccess { options ->
                // Verification challenge acquired
                val punchRes = attendanceRepository.punch(options.challengeId, mapOf("verified" to true))
                _loading.value = false
                punchRes.onSuccess {
                    val msg = if (direction == "IN") "Checked in successfully" else "Checked out successfully"
                    _notice.value = msg
                    onSuccess(msg)
                    refreshAllData()
                }.onFailure {
                    _error.value = it.message ?: "Punch verification failed"
                }
            }.onFailure {
                _loading.value = false
                _error.value = it.message ?: "Location check failed"
            }
        }
    }

    // Work Request Actions
    fun createWorkRequest(input: CreateWorkRequestInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            val res = requestRepository.createRequest(input)
            _loading.value = false
            res.onSuccess {
                _notice.value = "Work request submitted successfully"
                onSuccess()
                refreshAllData()
            }.onFailure {
                _error.value = it.message ?: "Failed to submit request"
            }
        }
    }

    fun decideRequest(id: String, decision: String, note: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = requestRepository.decideRequest(id, decision, note)
            _loading.value = false
            res.onSuccess {
                _notice.value = "Decision recorded"
                refreshAllData()
            }.onFailure {
                _error.value = it.message ?: "Decision failed"
            }
        }
    }

    fun payRequest(id: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = requestRepository.payRequest(id)
            _loading.value = false
            res.onSuccess {
                _notice.value = "Payment recorded"
                refreshAllData()
            }.onFailure {
                _error.value = it.message ?: "Payment recording failed"
            }
        }
    }

    // Payroll Actions
    fun loadPayroll() {
        viewModelScope.launch {
            _loading.value = true
            payrollRepository.getRuns().onSuccess { payrollRunsList.value = it }
            if (userRole == "ADMIN" || userRole == "FINANCE") {
                payrollRepository.getPolicies().onSuccess { payrollPoliciesList.value = it }
            }
            _loading.value = false
        }
    }

    fun calculatePayrollRun(month: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = payrollRepository.calculateRun(month)
            _loading.value = false
            res.onSuccess {
                _notice.value = "Payroll draft calculated for $month"
                loadPayroll()
            }.onFailure {
                _error.value = it.message ?: "Calculation failed"
            }
        }
    }

    fun finalizePayrollRun(id: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = payrollRepository.finalizeRun(id)
            _loading.value = false
            res.onSuccess {
                _notice.value = "Payroll run finalized"
                loadPayroll()
            }.onFailure {
                _error.value = it.message ?: "Finalization failed"
            }
        }
    }

    fun downloadPayslip(context: Context, lineId: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = payrollRepository.downloadPayslip(lineId)
            _loading.value = false
            res.onSuccess { body ->
                PdfHelper.saveAndOpenPdf(context, body, "payslip_$lineId.pdf")
            }.onFailure {
                _error.value = it.message ?: "Payslip download failed"
            }
        }
    }

    fun downloadTourBill(context: Context, requestId: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = requestRepository.downloadTourBill(requestId)
            _loading.value = false
            res.onSuccess { body ->
                PdfHelper.saveAndOpenPdf(context, body, "tour_bill_$requestId.pdf")
            }.onFailure {
                _error.value = it.message ?: "Tour bill download failed"
            }
        }
    }

    // Talent & Admin
    fun loadTalent() {
        viewModelScope.launch {
            _loading.value = true
            talentRepository.getPositions().onSuccess { positionsList.value = it }
            talentRepository.getOnboardingTasks().onSuccess { onboardingTasksList.value = it }
            talentRepository.getReviews().onSuccess { reviewsList.value = it }
            _loading.value = false
        }
    }

    fun loadAdmin() {
        viewModelScope.launch {
            _loading.value = true
            talentRepository.getAccessRequests().onSuccess { accessRequestsList.value = it }
            talentRepository.getAuditLogs().onSuccess { auditLogsList.value = it }
            _loading.value = false
        }
    }

    fun decideAccessRequest(id: String, decision: String, note: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = talentRepository.decideAccessRequest(id, decision, note)
            _loading.value = false
            res.onSuccess {
                _notice.value = it
                loadAdmin()
            }.onFailure {
                _error.value = it.message ?: "Failed to review request"
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            _user.value = null
            onSignedOut()
        }
    }

    fun clearError() { _error.value = null }
    fun clearNotice() { _notice.value = null }
}
