package com.daksha.hrms.data.api.models

import com.google.gson.JsonObject

data class WorkRequestDto(
    val id: String,
    val companyId: String,
    val employeeId: String,
    val kind: String, // LEAVE, TRAVEL, EXPENSE, ADVANCE, LOAN
    val title: String,
    val details: JsonObject,
    val amount: Int = 0,
    val status: String, // PENDING, APPROVED, REJECTED, PAID
    val approvalStep: Int = 0,
    val approvalRoles: List<String> = emptyList(),
    val createdAt: String,
    val canReview: Boolean = false,
    val canDownloadTour: Boolean = false,
    val employee: WorkRequestEmployeeDto? = null,
    val manager: WorkRequestManagerDto? = null,
    val decisions: List<DecisionDto> = emptyList()
)

data class WorkRequestEmployeeDto(
    val name: String,
    val code: String
)

data class WorkRequestManagerUserDto(
    val name: String,
    val active: Boolean
)

data class WorkRequestManagerDto(
    val id: String,
    val role: String,
    val userId: String? = null,
    val user: WorkRequestManagerUserDto? = null
)

data class DecisionDto(
    val id: String,
    val actorId: String,
    val step: Int,
    val decision: String,
    val note: String,
    val createdAt: String
)

data class CreateWorkRequestInput(
    val kind: String,
    val employeeId: String,
    val title: String,
    val details: Map<String, Any>,
    val amount: Int? = null
)

data class DecideWorkRequestInput(
    val decision: String, // APPROVE or REJECT
    val note: String
)
