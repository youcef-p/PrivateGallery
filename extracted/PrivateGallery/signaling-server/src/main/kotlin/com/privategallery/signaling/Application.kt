package com.privategallery.signaling

import com.privategallery.signaling.auth.JwtService
import com.privategallery.signaling.routes.authRoutes
import com.privategallery.signaling.routes.folderRoutes
import com.privategallery.signaling.ws.SignalingHub
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.slf4j.event.Level
import java.time.Duration

/**
 * Entry point for the reference signaling/relay server. Ships zero-knowledge by construction:
 * every column that could carry user content is either absent or ciphertext (see model/Tables.kt),
 * and CallLogging below is scoped to method+path+status only — bodies are never logged, per
 * SECURITY_REQUIREMENTS "Never log media content, decryption keys, or sensitive authentication
 * tokens."
 */
fun main() {
    Database.connect(
        url = System.getenv("DATABASE_URL") ?: error("DATABASE_URL env var must be set"),
        driver = "org.postgresql.Driver",
        user = System.getenv("DATABASE_USER") ?: error("DATABASE_USER env var must be set"),
        password = System.getenv("DATABASE_PASSWORD") ?: error("DATABASE_PASSWORD env var must be set")
    )

    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(WebSockets) { pingPeriod = Duration.ofSeconds(20); timeout = Duration.ofSeconds(30) }
        install(CallLogging) {
            level = Level.INFO
            // Explicitly do NOT log request/response bodies — see class doc above.
            format { call -> "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()}" }
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText("Internal error", status = HttpStatusCode.InternalServerError)
            }
        }
        install(Authentication) {
            jwt("jwt") {
                verifier(JwtService.verifier())
                validate { credential ->
                    if (credential.payload.getClaim("type").asString() == "access") JWTPrincipal(credential.payload) else null
                }
            }
        }

        routing {
            authRoutes()
            folderRoutes()

            authenticate("jwt") {
                webSocket("/ws") {
                    val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                        ?: return@webSocket close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "unauthenticated"))
                    SignalingHub.register(userId, this)
                }
            }
        }
    }.start(wait = true)
}
