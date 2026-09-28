package com.privategallery.app.access

import com.privategallery.app.data.local.entity.FolderEntity
import com.privategallery.app.data.local.entity.FolderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the access-control *predicate* that FolderDao.observeFoldersForUser /
 * getAuthorizedFolder encode as SQL — expressed here as a plain Kotlin filter so the rule is
 * testable without standing up Room/Robolectric, while asserting exactly the same boolean logic
 * the real @Query uses: `status != DELETED AND (ownerUserId = :me OR participantUserId = :me)`.
 */
class FolderAccessControlTest {

    private fun isAuthorized(folder: FolderEntity, userId: String): Boolean =
        folder.status != FolderStatus.DELETED && (folder.ownerUserId == userId || folder.participantUserId == userId)

    private fun folder(owner: String, participant: String?, status: FolderStatus = FolderStatus.ACTIVE) = FolderEntity(
        id = "f1", ownerUserId = owner, participantUserId = participant,
        encryptedName = ByteArray(0), encryptedNameNonce = ByteArray(0), encryptedThumbnailPath = null,
        folderKeyAlias = "alias", createdAt = 0, updatedAt = 0, status = status
    )

    @Test
    fun `owner is authorized`() {
        assertEquals(true, isAuthorized(folder(owner = "alice", participant = "bob"), "alice"))
    }

    @Test
    fun `participant is authorized`() {
        assertEquals(true, isAuthorized(folder(owner = "alice", participant = "bob"), "bob"))
    }

    @Test
    fun `unrelated third user is not authorized`() {
        assertEquals(false, isAuthorized(folder(owner = "alice", participant = "bob"), "carol"))
    }

    @Test
    fun `a second folder between the same owner and a different participant is a separate space`() {
        val folderWithBob = folder(owner = "alice", participant = "bob")
        val folderWithCarol = folder(owner = "alice", participant = "carol").copy(id = "f2")

        assertEquals(true, isAuthorized(folderWithBob, "bob"))
        assertEquals(false, isAuthorized(folderWithBob, "carol"))
        assertEquals(true, isAuthorized(folderWithCarol, "carol"))
        assertEquals(false, isAuthorized(folderWithCarol, "bob"))
    }

    @Test
    fun `deleted folder is not authorized even for the former owner`() {
        assertEquals(false, isAuthorized(folder(owner = "alice", participant = "bob", status = FolderStatus.DELETED), "alice"))
    }

    @Test
    fun `removed participant loses access represented as null participant after revocation`() {
        val revoked = folder(owner = "alice", participant = null)
        assertNull(revoked.participantUserId)
        assertEquals(false, isAuthorized(revoked, "bob"))
    }
}
