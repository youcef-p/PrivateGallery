package com.privategallery.app.data.remote.api

import com.privategallery.app.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login/challenge")
    suspend fun requestLoginChallenge(@Body body: LoginChallengeRequest): LoginChallengeResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest)

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest)

    @POST("auth/refresh")
    suspend fun refresh(@Body refreshToken: String): AuthResponse
}
