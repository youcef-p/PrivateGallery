package com.privategallery.signaling.routes

import com.privategallery.signaling.model.FolderMembers
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * The single choke point every folder/media/sync-event endpoint must call before doing anything
 * else. This is what makes "if a user is removed, their token becomes invalid" true in practice:
 * even a validly-signed, unexpired JWT gets rejected here the instant FolderMembers.status flips
 * to REMOVED, because we check the DB, not just the JWT claims.
 */
object AccessControl {
    fun isActiveMember(folderId: String, userId: String): Boolean = transaction {
        FolderMembers.select {
            (FolderMembers.folderId eq folderId) and
                (FolderMembers.userId eq userId) and
                (FolderMembers.status eq "ACTIVE")
        }.limit(1).any()
    }
}

private fun org.jetbrains.exposed.sql.Query.any(): Boolean = !this.empty()
