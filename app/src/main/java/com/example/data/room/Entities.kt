package com.example.data.room

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val group: String,
    val epgId: String
)

@Immutable
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val group: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val profileId: String,
    val name: String,
    val avatarUrl: String,
    val active: Boolean,
    val m3uUrl: String = ""
)
