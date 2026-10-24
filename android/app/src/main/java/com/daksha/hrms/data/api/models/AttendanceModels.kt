package com.daksha.hrms.data.api.models

data class BranchDto(
    val id: String,
    val name: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMetres: Int = 50,
    val active: Boolean = true
)

data class CredentialDto(
    val id: String,
    val label: String,
    val status: String, // PENDING, APPROVED, REVOKED
    val createdAt: String,
    val employee: EmployeeSimpleDto? = null
)

data class EmployeeSimpleDto(
    val name: String,
    val code: String
)

data class SecurityResponseDto(
    val branch: BranchDto? = null,
    val credentials: List<CredentialDto> = emptyList()
)

data class LocationInputDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double,
    val capturedAt: Long
)

data class PunchOptionsRequestDto(
    val direction: String, // IN or OUT
    val location: LocationInputDto
)

data class PunchOptionsResponseDto(
    val challengeId: String,
    val options: Any? = null
)

data class PunchRequestDto(
    val challengeId: String,
    val response: Map<String, Any>
)

data class AttendanceDto(
    val id: String,
    val companyId: String,
    val employeeId: String,
    val day: String,
    val checkIn: String,
    val checkOut: String? = null,
    val source: String = "SELF",
    val employee: EmployeeSimpleDto? = null
)

data class CreateBranchInput(
    val name: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val active: Boolean = true
)

data class AssignBranchInput(
    val employeeId: String,
    val branchId: String
)

data class ReviewCredentialInput(
    val decision: String // APPROVED or REVOKED
)
