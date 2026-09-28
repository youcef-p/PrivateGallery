package com.privategallery.app.data.remote.ws

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SignalingEnvelope(
    val type: String,
    val from: String? = null,
    val to: String? = null,
    val folderId: String? = null,
    val payload: JsonElement
)

object SignalingType {
    const val PRESENCE = "presence"
    const val RTC_OFFER = "rtc-offer"
    const val RTC_ANSWER = "rtc-answer"
    const val RTC_ICE = "rtc-ice"
    const val INVITE_CREATED = "invite-created"
    const val INVITE_ACCEPTED = "invite-accepted"
    const val SYNC_EVENT = "sync-event"
    const val BLOB_READY = "blob-ready"
    const val MEMBER_REMOVED = "member-removed"
}
