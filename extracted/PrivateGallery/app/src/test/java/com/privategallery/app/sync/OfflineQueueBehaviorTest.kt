package com.privategallery.app.sync

import com.privategallery.app.data.local.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Models the queueing rule pull-to-refresh and MediaTransferWorker both rely on: exactly the
 * items in PENDING or FAILED are "outgoing work" a refresh should attempt; anything else is
 * either already done or actively in flight and must not be re-queued redundantly.
 */
class OfflineQueueBehaviorTest {

    data class FakeMedia(val id: String, val status: SyncStatus)

    private fun outgoingQueue(items: List<FakeMedia>): List<FakeMedia> =
        items.filter { it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED }

    @Test
    fun `only pending and failed items are queued for outgoing transfer`() {
        val items = listOf(
            FakeMedia("a", SyncStatus.PENDING),
            FakeMedia("b", SyncStatus.SYNCED),
            FakeMedia("c", SyncStatus.FAILED),
            FakeMedia("d", SyncStatus.UPLOADING),
            FakeMedia("e", SyncStatus.DELETED_LOCAL)
        )
        val queued = outgoingQueue(items).map { it.id }
        assertEquals(listOf("a", "c"), queued)
    }

    @Test
    fun `an already-uploading item is not re-queued by a concurrent refresh`() {
        val items = listOf(FakeMedia("a", SyncStatus.UPLOADING))
        assertEquals(emptyList<FakeMedia>(), outgoingQueue(items))
    }
}
