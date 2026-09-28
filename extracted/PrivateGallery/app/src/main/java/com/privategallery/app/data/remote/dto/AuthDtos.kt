package com.privategallery.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String, // sent over TLS only; hashed server-side with Argon2id, never logged
    val publicKeyX25519: String,
    val publicKeyEd25519: String,
    val deviceId: String
)

@Serializable
data class LoginChallengeRequest(val usernameOrEmail: String)

@Serializable
data class LoginChallengeResponse(val challenge: String, val challengeId: String)

@Serializable
data class LoginRequest(
    val usernameOrEmail: String,
    val password: String,
    val challengeId: String,
    val signedChallenge: String // Ed25519 signature over the challenge, proves device possession
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresAt: Long
)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(val resetToken: String, val newPassword: String)
