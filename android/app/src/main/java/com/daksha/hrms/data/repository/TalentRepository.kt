package com.daksha.hrms.data.repository

import com.daksha.hrms.data.api.DakshaApiService
import com.daksha.hrms.data.api.models.*

class TalentRepository(private val api: DakshaApiService) {

    suspend fun getPositions(): Result<List<PositionDto>> {
        return try {
            val response = api.getPositions()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch positions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPosition(input: CreatePositionInput): Result<PositionDto> {
        return try {
            val response = api.createPosition(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to open position"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCandidate(input: CreateCandidateInput): Result<CandidateDto> {
        return try {
            val response = api.createCandidate(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to add candidate"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCandidateStage(id: String, stage: String): Result<Boolean> {
        return try {
            val response = api.updateCandidateStage(id, UpdateCandidateStageInput(stage))
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update stage"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOnboardingTasks(): Result<List<OnboardingTaskDto>> {
        return try {
            val response = api.getOnboardingTasks()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load onboarding tasks"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOnboardingTask(input: CreateOnboardingTaskInput): Result<OnboardingTaskDto> {
        return try {
            val response = api.createOnboardingTask(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to create task"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeOnboardingTask(id: String): Result<OnboardingTaskDto> {
        return try {
            val response = api.completeOnboardingTask(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to complete task"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReviews(): Result<List<ReviewDto>> {
        return try {
            val response = api.getReviews()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load reviews"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createReview(input: CreateReviewInput): Result<ReviewDto> {
        return try {
            val response = api.createReview(input)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to start review"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateReview(id: String, feedback: String, rating: Int): Result<Boolean> {
        return try {
            val response = api.updateReview(id, UpdateReviewInput(feedback, rating))
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to submit feedback"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAccessRequests(): Result<List<AccessRequestDto>> {
        return try {
            val response = api.getAccessRequests()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch access requests"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decideAccessRequest(id: String, decision: String, note: String): Result<String> {
        return try {
            val response = api.decideAccessRequest(id, AccessDecisionInput(decision, note))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!["message"] ?: "Decision recorded")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to decide access request"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAuditLogs(): Result<List<AuditDto>> {
        return try {
            val response = api.getAuditLogs()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch audit logs"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
