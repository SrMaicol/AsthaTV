package com.example.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM favorites")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun removeFavorite(id: String)

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT 20")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistoryEntity)

    @Query("SELECT * FROM user_profiles")
    fun getUserProfiles(): Flow<List<UserProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesList(): List<FavoriteEntity>

    @Query("SELECT * FROM user_profiles")
    suspend fun getAllProfilesList(): List<UserProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<UserProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM user_profiles WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Query("UPDATE user_profiles SET active = 0")
    suspend fun clearActiveProfiles()

    @Query("UPDATE user_profiles SET active = 1 WHERE profileId = :profileId")
    suspend fun setProfileActive(profileId: String)
}
