package com.daksha.hrms.data.api.models

data class PayrollPolicyDto(
    val id: String,
    val companyId: String,
    val name: String,
    val effectiveFrom: String,
    val pfEmployeeBps: Int,
    val pfEmployerBps: Int,
    val pfCeiling: Int,
    val esicEmployeeBps: Int,
    val esicEmployerBps: Int,
    val esicCeiling: Int,
    val professionalTax: Int = 0,
    val approvedBy: String,
    val approvedAt: String
)

data class CreatePolicyInput(
    val name: String,
    val effectiveFrom: String,
    val pfEmployeeBps: Int,
    val pfEmployerBps: Int,
    val pfCeiling: Int,
    val esicEmployeeBps: Int,
    val esicEmployerBps: Int,
    val esicCeiling: Int,
    val professionalTax: Int,
    val confirmedReviewed: Boolean = true
)

data class PayrollLineDto(
    val id: String,
    val runId: String,
    val employeeId: String,
    val basic: Int,
    val allowance: Int,
    val pfEmployee: Int,
    val pfEmployer: Int,
    val esicEmployee: Int,
    val esicEmployer: Int,
    val tds: Int,
    val professionalTax: Int,
    val net: Int,
    val employee: EmployeeSimpleDto? = null
)

data class PayrollRunDto(
    val id: String,
    val companyId: String,
    val month: String,
    val status: String, // DRAFT or FINALIZED
    val policyId: String,
    val createdBy: String,
    val finalizedBy: String? = null,
    val createdAt: String,
    val lines: List<PayrollLineDto> = emptyList()
)

data class CalculateRunInput(
    val month: String // YYYY-MM
)
