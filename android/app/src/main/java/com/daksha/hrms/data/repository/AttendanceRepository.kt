package com.daksha.hrms.data.repository

import com.daksha.hrms.data.api.DakshaApiService
import com.daksha.hrms.data.api.models.*

class AttendanceRepository(private val api: DakshaApiService) {

    suspend fun getAttendance(): Result<List<AttendanceDto>> {
        return try {
            val response = api.getAttendance()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load attendance records"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSecurity(): Result<SecurityResponseDto> {
        return try {
            val response = api.getAttendanceSecurity()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load attendance security details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBranches(): Result<List<BranchDto>> {
        return try {
            val response = api.getBranches()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load branches"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBranch(input: CreateBranchInput): Result<BranchDto> {
        return try {
            val response = api.createBranch(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to save branch"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBranch(id: String, input: CreateBranchInput): Result<Boolean> {
        return try {
            val response = api.updateBranch(id, input)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update branch"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignBranch(employeeId: String, branchId: String): Result<Boolean> {
        return try {
            val response = api.assignBranch(AssignBranchInput(employeeId, branchId))
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to assign branch"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCredentials(): Result<List<CredentialDto>> {
        return try {
            val response = api.getCredentials()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load credentials"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reviewCredential(id: String, decision: String): Result<Boolean> {
        return try {
            val response = api.reviewCredential(id, ReviewCredentialInput(decision))
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to review passkey"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun punchOptions(direction: String, lat: Double, lng: Double, accuracy: Double, capturedAt: Long): Result<PunchOptionsResponseDto> {
        return try {
            val input = PunchOptionsRequestDto(
                direction = direction,
                location = LocationInputDto(lat, lng, accuracy, capturedAt)
            )
            val response = api.punchOptions(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Location validation failed"
                Result.failure(Exception(extractErrorMessage(errorMsg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun punch(challengeId: String, responsePayload: Map<String, Any>): Result<AttendanceDto> {
        return try {
            val response = api.punch(PunchRequestDto(challengeId, responsePayload))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Check-in/out verification failed"
                Result.failure(Exception(extractErrorMessage(errorMsg)))
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
            raw.ifBlank { "Location check failed" }
        } catch (_: Exception) {
            raw
        }
    }
}
