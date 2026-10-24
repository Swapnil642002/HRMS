package com.daksha.hrms.data.repository

import com.daksha.hrms.data.api.DakshaApiService
import com.daksha.hrms.data.api.models.*
import okhttp3.ResponseBody

class RequestRepository(private val api: DakshaApiService) {

    suspend fun getRequests(): Result<List<WorkRequestDto>> {
        return try {
            val response = api.getRequests()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load work requests"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getManagers(): Result<List<WorkRequestManagerDto>> {
        return try {
            val response = api.getManagers()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load managers"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignManager(employeeId: String, managerId: String): Result<Boolean> {
        return try {
            val response = api.assignManager(AssignManagerInput(employeeId, managerId))
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to assign manager"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRequest(input: CreateWorkRequestInput): Result<WorkRequestDto> {
        return try {
            val response = api.createRequest(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to submit request"
                Result.failure(Exception(extractErrorMessage(errorMsg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decideRequest(id: String, decision: String, note: String): Result<WorkRequestDto> {
        return try {
            val response = api.decideRequest(id, DecideWorkRequestInput(decision, note))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to record decision"
                Result.failure(Exception(extractErrorMessage(errorMsg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun payRequest(id: String): Result<WorkRequestDto> {
        return try {
            val response = api.payRequest(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to mark paid"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadTourBill(id: String): Result<ResponseBody> {
        return try {
            val response = api.downloadTourBill(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to download tour bill"))
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
            raw.ifBlank { "Error processing request" }
        } catch (_: Exception) {
            raw
        }
    }
}
