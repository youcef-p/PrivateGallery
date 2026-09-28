package com.privategallery.app.sync

import com.privategallery.app.data.local.entity.SyncStatus
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents and enforces the legal sync-state transition graph used by MediaRepositoryImpl /
 * MediaTransferWorker, so a future change can't silently introduce an invalid jump (e.g.
 * PENDING -> SYNCED without ever going through UPLOADING).
 */
class MediaSyncStateTransitionTest {

    private val allowedTransitions: Map<SyncStatus, Set<SyncStatus>> = mapOf(
        SyncStatus.PENDING to setOf(SyncStatus.UPLOADING, SyncStatus.FAILED, SyncStatus.DELETED_LOCAL),
        SyncStatus.UPLOADING to setOf(SyncStatus.SYNCED, SyncStatus.FAILED, SyncStatus.DELETED_LOCAL),
        SyncStatus.DOWNLOADING to setOf(SyncStatus.SYNCED, SyncStatus.FAILED),
        SyncStatus.FAILED to setOf(SyncStatus.UPLOADING, SyncStatus.DOWNLOADING, SyncStatus.DELETED_LOCAL),
        SyncStatus.SYNCED to setOf(SyncStatus.DELETED_LOCAL, SyncStatus.DELETED_REMOTE),
        SyncStatus.DELETED_LOCAL to emptySet(),
        SyncStatus.DELETED_REMOTE to emptySet()
    )

    private fun isValidTransition(from: SyncStatus, to: SyncStatus) =
        from == to || allowedTransitions[from]?.contains(to) == true

    @Test
    fun `pending can move to uploading`() = assertTrue(isValidTransition(SyncStatus.PENDING, SyncStatus.UPLOADING))

    @Test
    fun `uploading can complete to synced`() = assertTrue(isValidTransition(SyncStatus.UPLOADING, SyncStatus.SYNCED))

    @Test
    fun `failed transfer can be retried from failed to uploading`() = assertTrue(isValidTransition(SyncStatus.FAILED, SyncStatus.UPLOADING))

    @Test
    fun `synced item can be locally deleted`() = assertTrue(isValidTransition(SyncStatus.SYNCED, SyncStatus.DELETED_LOCAL))

    @Test
    fun `pending cannot jump directly to synced`() {
        assertTrue(!isValidTransition(SyncStatus.PENDING, SyncStatus.SYNCED))
    }

    @Test
    fun `deleted states are terminal`() {
        assertTrue(allowedTransitions[SyncStatus.DELETED_LOCAL]!!.isEmpty())
        assertTrue(allowedTransitions[SyncStatus.DELETED_REMOTE]!!.isEmpty())
    }
}
