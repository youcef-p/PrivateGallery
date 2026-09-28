package com.privategallery.signaling.auth

import de.mkammerer.argon2.Argon2Factory

/**
 * Argon2id password hashing per AUTHENTICATION requirements. Parameters follow OWASP's current
 * baseline recommendation for Argon2id (memory-hard, tuned for an interactive login path):
 * 19 MiB memory, 2 iterations, degree of parallelism 1 — adjust iterations upward if server
 * hardware allows a higher login-latency budget; never reduce memory below ~19 MiB.
 */
object PasswordHasher {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    private const val MEMORY_KB = 19 * 1024
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1

    fun hash(password: CharArray): String =
        try {
            argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, password)
        } finally {
            argon2.wipeArray(password)
        }

    fun verify(hash: String, password: CharArray): Boolean =
        try {
            argon2.verify(hash, password)
        } finally {
            argon2.wipeArray(password)
        }
}
