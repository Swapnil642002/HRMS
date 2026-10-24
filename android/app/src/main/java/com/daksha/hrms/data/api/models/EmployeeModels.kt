package com.daksha.hrms.data.api.models

data class ReportingManagerUserDto(
    val name: String,
    val active: Boolean
)

data class ReportingManagerDto(
    val id: String,
    val role: String,
    val userId: String? = null,
    val user: ReportingManagerUserDto? = null
)

data class EmployeeDto(
    val id: String,
    val companyId: String,
    val code: String,
    val name: String,
    val email: String,
    val department: String,
    val designation: String,
    val joinedOn: String,
    val active: Boolean = true,
    val monthlyBasic: Int = 0,
    val monthlyAllowance: Int = 0,
    val monthlyTds: Int = 0,
    val pfEnabled: Boolean = true,
    val esicEnabled: Boolean = false,
    val leaveAllowance: Int = 24,
    val branchId: String? = null,
    val reportingManagerId: String? = null,
    val reportingManager: ReportingManagerDto? = null
)

data class CreateEmployeeInput(
    val code: String,
    val name: String,
    val email: String,
    val department: String,
    val designation: String,
    val joinedOn: String,
    val monthlyBasic: Int,
    val monthlyAllowance: Int = 0,
    val monthlyTds: Int = 0,
    val pfEnabled: Boolean = true,
    val esicEnabled: Boolean = false
)

data class DepartmentStat(
    val name: String,
    val count: Int
)

data class DashboardResponse(
    val employees: Int = 0,
    val pending: Int = 0,
    val present: Int = 0,
    val departments: List<DepartmentStat> = emptyList()
)

data class CreateMemberInput(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val employeeId: String? = null
)

data class AssignManagerInput(
    val employeeId: String,
    val managerId: String
)
