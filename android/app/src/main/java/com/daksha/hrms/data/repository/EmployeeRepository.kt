package com.daksha.hrms.data.repository

import com.daksha.hrms.data.api.DakshaApiService
import com.daksha.hrms.data.api.models.*

class EmployeeRepository(private val api: DakshaApiService) {

    suspend fun getDashboard(): Result<DashboardResponse> {
        return try {
            val response = api.getDashboard()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load dashboard statistics"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEmployees(): Result<List<EmployeeDto>> {
        return try {
            val response = api.getEmployees()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch employees list"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createEmployee(input: CreateEmployeeInput): Result<EmployeeDto> {
        return try {
            val response = api.createEmployee(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to create employee"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createMember(input: CreateMemberInput): Result<Boolean> {
        return try {
            val response = api.createMember(input)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to add member"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCompany(name: String): Result<CompanyDto> {
        return try {
            val response = api.createCompany(CreateCompanyInput(name))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to create company"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
