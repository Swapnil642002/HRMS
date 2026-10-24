package com.daksha.hrms.data.api.models

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class ApiResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val status: String? = null
)

data class CompanyDto(
    val id: String,
    val name: String,
    val timezone: String? = "Asia/Kolkata"
)

data class MembershipDto(
    val company: CompanyDto,
    val role: String,
    val employeeId: String? = null
)

data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    val memberships: List<MembershipDto> = emptyList()
)

data class AccessRequestInput(
    val companyId: String,
    val employeeCode: String,
    val name: String,
    val email: String
)

data class ForgotPasswordInput(
    val email: String
)

data class ChangePasswordInput(
    val currentPassword: String,
    val password: String
)

data class AccessRequestDto(
    val id: String,
    val companyId: String,
    val email: String,
    val name: String,
    val employeeCode: String,
    val status: String,
    val reviewNote: String? = null,
    val createdAt: String
)

data class AccessDecisionInput(
    val decision: String, // APPROVE or REJECT
    val note: String = ""
)
