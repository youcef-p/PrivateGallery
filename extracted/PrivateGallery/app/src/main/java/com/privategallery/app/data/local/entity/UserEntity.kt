package com.privategallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val publicKeyX25519: String, // base64
    val publicKeyEd25519: String, // base64
    val deviceId: String,
    val createdAt: Long
)
