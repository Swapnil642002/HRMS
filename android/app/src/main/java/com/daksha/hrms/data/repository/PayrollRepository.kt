package com.daksha.hrms.data.repository

import com.daksha.hrms.data.api.DakshaApiService
import com.daksha.hrms.data.api.models.*
import okhttp3.ResponseBody

class PayrollRepository(private val api: DakshaApiService) {

    suspend fun getPolicies(): Result<List<PayrollPolicyDto>> {
        return try {
            val response = api.getPolicies()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch policies"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPolicy(input: CreatePolicyInput): Result<PayrollPolicyDto> {
        return try {
            val response = api.createPolicy(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to save policy"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRuns(): Result<List<PayrollRunDto>> {
        return try {
            val response = api.getPayrollRuns()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load payroll runs"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun calculateRun(month: String): Result<PayrollRunDto> {
        return try {
            val response = api.calculatePayrollRun(CalculateRunInput(month))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to calculate payroll run"
                Result.failure(Exception(extractErrorMessage(errorMsg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun finalizeRun(id: String): Result<Boolean> {
        return try {
            val response = api.finalizePayrollRun(id)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to finalize payroll run"
                Result.failure(Exception(extractErrorMessage(errorMsg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadPayslip(id: String): Result<ResponseBody> {
        return try {
            val response = api.downloadPayslip(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to download payslip PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(raw: String): String {
        return try {
            if (raw.contains("\"message\":")) {
                val idx = raw.indexOf("\"message\":\"")
                if (idx != -1) {
                    val start = idx + 11
                    val end = raw.indexOf("\"", start)
                    if (end != -1) return raw.substring(start, end)
                }
            }
            raw.ifBlank { "Payroll error" }
        } catch (_: Exception) {
            raw
        }
    }
}
