package com.daksha.hrms.data.api.models

data class PositionDto(
    val id: String,
    val companyId: String,
    val title: String,
    val department: String,
    val openings: Int,
    val status: String,
    val candidates: List<CandidateDto> = emptyList()
)

data class CreatePositionInput(
    val title: String,
    val department: String,
    val openings: Int
)

data class CandidateDto(
    val id: String,
    val companyId: String,
    val positionId: String,
    val name: String,
    val email: String,
    val stage: String // APPLIED, SCREENING, INTERVIEW, OFFER, HIRED, REJECTED
)

data class CreateCandidateInput(
    val positionId: String,
    val name: String,
    val email: String
)

data class UpdateCandidateStageInput(
    val stage: String
)

data class OnboardingTaskDto(
    val id: String,
    val companyId: String,
    val employeeId: String,
    val title: String,
    val dueOn: String,
    val completedAt: String? = null,
    val employee: EmployeeSimpleDto? = null
)

data class CreateOnboardingTaskInput(
    val employeeId: String,
    val title: String,
    val dueOn: String
)

data class ReviewDto(
    val id: String,
    val companyId: String,
    val employeeId: String,
    val cycle: String,
    val goals: String,
    val feedback: String? = null,
    val rating: Int? = null,
    val status: String, // OPEN or COMPLETED
    val employee: EmployeeSimpleDto? = null
)

data class CreateReviewInput(
    val employeeId: String,
    val cycle: String,
    val goals: String
)

data class UpdateReviewInput(
    val feedback: String,
    val rating: Int,
    val status: String = "COMPLETED"
)

data class AuditDto(
    val id: String,
    val companyId: String,
    val actorId: String,
    val action: String,
    val entityId: String,
    val createdAt: String
)

data class StoredFileResponse(
    val id: String,
    val url: String
)

data class CreateCompanyInput(
    val name: String
)
