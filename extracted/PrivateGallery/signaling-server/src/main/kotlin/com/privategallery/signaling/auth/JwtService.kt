package com.privategallery.signaling.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/**
 * Issues short-lived access tokens (15 min) + longer refresh tokens (30 days). The signing
 * secret is loaded from an environment variable (JWT_SECRET) — never hardcoded, never logged.
 * Folder-scoped claims are NOT embedded in the JWT itself; every folder/media endpoint re-checks
 * the `folder_members` table (see routes/FolderRoutes.kt), so a stale-but-unexpired JWT cannot
 * grant access to a folder the user was removed from — see docs/SECURITY.md § Access control.
 */
object JwtService {
    private val secret = System.getenv("JWT_SECRET") ?: error("JWT_SECRET env var must be set")
    private val algorithm = Algorithm.HMAC256(secret)
    private const val ISSUER = "private-gallery-signaling"

    fun createAccessToken(userId: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withClaim("userId", userId)
        .withClaim("type", "access")
        .withExpiresAt(Date(System.currentTimeMillis() + 15 * 60 * 1000))
        .sign(algorithm)

    fun createRefreshToken(userId: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withClaim("userId", userId)
        .withClaim("type", "refresh")
        .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
        .sign(algorithm)

    fun verifier() = JWT.require(algorithm).withIssuer(ISSUER).build()
}
