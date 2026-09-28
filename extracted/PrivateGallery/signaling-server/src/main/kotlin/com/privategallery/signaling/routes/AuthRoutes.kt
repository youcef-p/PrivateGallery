package com.privategallery.signaling.routes

import com.privategallery.signaling.auth.JwtService
import com.privategallery.signaling.auth.PasswordHasher
import com.privategallery.signaling.model.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable data class RegisterRequest(val username: String, val email: String, val password: String, val publicKeyX25519: String, val publicKeyEd25519: String, val deviceId: String)
@Serializable data class LoginChallengeRequest(val usernameOrEmail: String)
@Serializable data class LoginChallengeResponse(val challenge: String, val challengeId: String)
@Serializable data class LoginRequest(val usernameOrEmail: String, val password: String, val challengeId: String, val signedChallenge: String)
@Serializable data class AuthResponse(val accessToken: String, val refreshToken: String, val userId: String, val expiresAt: Long)

// In-memory challenge store keyed by challengeId; production swaps this for Redis with a short TTL.
private val pendingChallenges = ConcurrentHashMap<String, Pair<String, String>>() // challengeId -> (challenge, userId)

fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
            val body = call.receive<RegisterRequest>()
            val emailHash = hmacEmail(body.email)

            val exists = transaction {
                Users.select { (Users.username eq body.username) or (Users.emailHash eq emailHash) }.any()
            }
            if (exists) return@post call.respond(HttpStatusCode.Conflict, "Username or email already registered")

            val userId = UUID.randomUUID().toString()
            val hashed = PasswordHasher.hash(body.password.toCharArray())

            transaction {
                Users.insert {
                    it[id] = userId
                    it[username] = body.username
                    it[Users.emailHash] = emailHash
                    it[passwordArgon2id] = hashed
                    it[pubkeyX25519] = body.publicKeyX25519
                    it[pubkeyEd25519] = body.publicKeyEd25519
                    it[deviceId] = body.deviceId
                    it[createdAt] = Instant.now()
                }
            }

            call.respond(
                AuthResponse(
                    accessToken = JwtService.createAccessToken(userId),
                    refreshToken = JwtService.createRefreshToken(userId),
                    userId = userId,
                    expiresAt = System.currentTimeMillis() + 15 * 60 * 1000
                )
            )
        }

        post("/login/challenge") {
            val body = call.receive<LoginChallengeRequest>()
            val emailHash = hmacEmail(body.usernameOrEmail)
            val userId = transaction {
                Users.select { (Users.username eq body.usernameOrEmail) or (Users.emailHash eq emailHash) }
                    .singleOrNull()?.get(Users.id)
            } ?: return@post call.respond(HttpStatusCode.NotFound, "No such account")

            val challenge = Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
            val challengeId = UUID.randomUUID().toString()
            pendingChallenges[challengeId] = challenge to userId
            call.respond(LoginChallengeResponse(challenge, challengeId))
        }

        post("/login") {
            val body = call.receive<LoginRequest>()
            val (challenge, userId) = pendingChallenges.remove(body.challengeId)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Challenge expired or invalid")

            val row = transaction { Users.select { Users.id eq userId }.single() }

            // 1. Account password check (Argon2id) — gates the *account*.
            if (!PasswordHasher.verify(row[Users.passwordArgon2id], body.password.toCharArray())) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
            // 2. Device-key proof (Ed25519 signature over the challenge) — gates *this device*
            //    actually holding the private key that matches the public key on file, so an
            //    attacker who only phished the account password still cannot complete login
            //    without also possessing this device's Keystore-resident private key.
            val pubKeyBytes = Base64.getDecoder().decode(row[Users.pubkeyEd25519])
            val sigBytes = Base64.getDecoder().decode(body.signedChallenge)
            val verified = com.google.crypto.tink.subtle.Ed25519Verify(pubKeyBytes)
            val valid = try { verified.verify(sigBytes, challenge.toByteArray()); true } catch (e: Exception) { false }
            if (!valid) return@post call.respond(HttpStatusCode.Unauthorized, "Device key verification failed")

            call.respond(
                AuthResponse(
                    accessToken = JwtService.createAccessToken(userId),
                    refreshToken = JwtService.createRefreshToken(userId),
                    userId = userId,
                    expiresAt = System.currentTimeMillis() + 15 * 60 * 1000
                )
            )
        }
    }
}

private fun hmacEmail(email: String): String {
    // HMAC with a server-side pepper so the emailHash column can't be trivially reversed by
    // rainbow-tabling common emails, while still supporting exact-match lookup for login.
    val pepper = System.getenv("EMAIL_HMAC_PEPPER") ?: error("EMAIL_HMAC_PEPPER env var must be set")
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(pepper.toByteArray(), "HmacSHA256"))
    return Base64.getEncoder().encodeToString(mac.doFinal(email.lowercase().trim().toByteArray()))
}
