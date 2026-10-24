package com.daksha.hrms.data.api

import com.daksha.hrms.data.api.models.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface DakshaApiService {

    @GET("health")
    suspend fun healthCheck(): Response<Map<String, String>>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<UserResponse>

    @POST("auth/request-access")
    suspend fun requestAccess(@Body body: AccessRequestInput): Response<Map<String, String>>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordInput): Response<Map<String, String>>

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordInput): Response<Map<String, String>>

    @GET("dashboard")
    suspend fun getDashboard(): Response<DashboardResponse>

    @GET("employees")
    suspend fun getEmployees(): Response<List<EmployeeDto>>

    @POST("employees")
    suspend fun createEmployee(@Body body: CreateEmployeeInput): Response<EmployeeDto>

    @PATCH("employees/{id}")
    suspend fun updateEmployee(@Path("id") id: String, @Body body: Map<String, Any>): Response<ApiResponse>

    @POST("members")
    suspend fun createMember(@Body body: CreateMemberInput): Response<Map<String, String>>

    @POST("companies")
    suspend fun createCompany(@Body body: CreateCompanyInput): Response<CompanyDto>

    @GET("attendance")
    suspend fun getAttendance(): Response<List<AttendanceDto>>

    @GET("attendance/security")
    suspend fun getAttendanceSecurity(): Response<SecurityResponseDto>

    @GET("attendance/branches")
    suspend fun getBranches(): Response<List<BranchDto>>

    @POST("attendance/branches")
    suspend fun createBranch(@Body body: CreateBranchInput): Response<BranchDto>

    @POST("attendance/branches/{id}")
    suspend fun updateBranch(@Path("id") id: String, @Body body: CreateBranchInput): Response<ApiResponse>

    @POST("attendance/assignments")
    suspend fun assignBranch(@Body body: AssignBranchInput): Response<ApiResponse>

    @GET("attendance/credentials")
    suspend fun getCredentials(): Response<List<CredentialDto>>

    @POST("attendance/credentials/{id}/review")
    suspend fun reviewCredential(@Path("id") id: String, @Body body: ReviewCredentialInput): Response<ApiResponse>

    @POST("attendance/punch/options")
    suspend fun punchOptions(@Body body: PunchOptionsRequestDto): Response<PunchOptionsResponseDto>

    @POST("attendance/punch")
    suspend fun punch(@Body body: PunchRequestDto): Response<AttendanceDto>

    @GET("requests/managers")
    suspend fun getManagers(): Response<List<WorkRequestManagerDto>>

    @POST("requests/assign-manager")
    suspend fun assignManager(@Body body: AssignManagerInput): Response<ApiResponse>

    @GET("requests")
    suspend fun getRequests(): Response<List<WorkRequestDto>>

    @POST("requests")
    suspend fun createRequest(@Body body: CreateWorkRequestInput): Response<WorkRequestDto>

    @POST("requests/{id}/decide")
    suspend fun decideRequest(@Path("id") id: String, @Body body: DecideWorkRequestInput): Response<WorkRequestDto>

    @POST("requests/{id}/pay")
    suspend fun payRequest(@Path("id") id: String): Response<WorkRequestDto>

    @GET("requests/{id}/tour-bill.pdf")
    @Streaming
    suspend fun downloadTourBill(@Path("id") id: String): Response<ResponseBody>

    @GET("payroll/policies")
    suspend fun getPolicies(): Response<List<PayrollPolicyDto>>

    @POST("payroll/policies")
    suspend fun createPolicy(@Body body: CreatePolicyInput): Response<PayrollPolicyDto>

    @GET("payroll/runs")
    suspend fun getPayrollRuns(): Response<List<PayrollRunDto>>

    @POST("payroll/runs")
    suspend fun calculatePayrollRun(@Body body: CalculateRunInput): Response<PayrollRunDto>

    @POST("payroll/runs/{id}/finalize")
    suspend fun finalizePayrollRun(@Path("id") id: String): Response<ApiResponse>

    @GET("payroll/lines/{id}/payslip.pdf")
    @Streaming
    suspend fun downloadPayslip(@Path("id") id: String): Response<ResponseBody>

    @GET("positions")
    suspend fun getPositions(): Response<List<PositionDto>>

    @POST("positions")
    suspend fun createPosition(@Body body: CreatePositionInput): Response<PositionDto>

    @POST("candidates")
    suspend fun createCandidate(@Body body: CreateCandidateInput): Response<CandidateDto>

    @PATCH("candidates/{id}")
    suspend fun updateCandidateStage(@Path("id") id: String, @Body body: UpdateCandidateStageInput): Response<ApiResponse>

    @GET("onboarding")
    suspend fun getOnboardingTasks(): Response<List<OnboardingTaskDto>>

    @POST("onboarding")
    suspend fun createOnboardingTask(@Body body: CreateOnboardingTaskInput): Response<OnboardingTaskDto>

    @PATCH("onboarding/{id}")
    suspend fun completeOnboardingTask(@Path("id") id: String): Response<OnboardingTaskDto>

    @GET("reviews")
    suspend fun getReviews(): Response<List<ReviewDto>>

    @POST("reviews")
    suspend fun createReview(@Body body: CreateReviewInput): Response<ReviewDto>

    @PATCH("reviews/{id}")
    suspend fun updateReview(@Path("id") id: String, @Body body: UpdateReviewInput): Response<ApiResponse>

    @GET("access-requests")
    suspend fun getAccessRequests(): Response<List<AccessRequestDto>>

    @POST("access-requests/{id}/decide")
    suspend fun decideAccessRequest(@Path("id") id: String, @Body body: AccessDecisionInput): Response<Map<String, String>>

    @GET("audit")
    suspend fun getAuditLogs(): Response<List<AuditDto>>

    @POST("files/upload-url")
    suspend fun getUploadUrl(@Body body: Map<String, String>): Response<StoredFileResponse>

    @GET("files/{id}/download-url")
    suspend fun getDownloadUrl(@Path("id") id: String): Response<Map<String, String>>
}
