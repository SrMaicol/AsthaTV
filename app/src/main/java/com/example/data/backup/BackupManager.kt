package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.room.FavoriteEntity
import com.example.data.room.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class BackupPayload(
    val profiles: List<UserProfileEntity>,
    val favorites: List<FavoriteEntity>,
    val timestamp: Long = System.currentTimeMillis()
)

object BackupManager {

    suspend fun exportToJson(
        context: Context,
        uri: Uri,
        profiles: List<UserProfileEntity>,
        favorites: List<FavoriteEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put("version", 1)
                put("appName", "IPTV Stream TV")
                put("timestamp", System.currentTimeMillis())

                val profilesArray = JSONArray()
                profiles.forEach { p ->
                    val obj = JSONObject().apply {
                        put("profileId", p.profileId)
                        put("name", p.name)
                        put("avatarUrl", p.avatarUrl)
                        put("active", p.active)
                        put("m3uUrl", p.m3uUrl)
                    }
                    profilesArray.put(obj)
                }
                put("profiles", profilesArray)

                val favoritesArray = JSONArray()
                favorites.forEach { f ->
                    val obj = JSONObject().apply {
                        put("id", f.id)
                        put("name", f.name)
                        put("streamUrl", f.streamUrl)
                        put("logoUrl", f.logoUrl)
                        put("group", f.group)
                        put("epgId", f.epgId)
                    }
                    favoritesArray.put(obj)
                }
                put("favorites", favoritesArray)
            }

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write(root.toString(2))
                }
            } ?: return@withContext Result.failure(Exception("No se pudo abrir el destino para escribir"))

            Result.success(profiles.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromJson(
        context: Context,
        uri: Uri
    ): Result<BackupPayload> = withContext(Dispatchers.IO) {
        try {
            val content = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        content.append(line)
                        line = reader.readLine()
                    }
                }
            } ?: return@withContext Result.failure(Exception("No se pudo abrir el archivo seleccionado"))

            val root = JSONObject(content.toString())
            val profilesList = mutableListOf<UserProfileEntity>()
            val favoritesList = mutableListOf<FavoriteEntity>()

            val profilesArray = root.optJSONArray("profiles")
            if (profilesArray != null) {
                for (i in 0 until profilesArray.length()) {
                    val obj = profilesArray.getJSONObject(i)
                    profilesList.add(
                        UserProfileEntity(
                            profileId = obj.optString("profileId", "prof_${System.currentTimeMillis()}_$i"),
                            name = obj.optString("name", "Perfil ${i + 1}"),
                            avatarUrl = obj.optString("avatarUrl", "avatar_lion"),
                            active = obj.optBoolean("active", i == 0),
                            m3uUrl = obj.optString("m3uUrl", "")
                        )
                    )
                }
            }

            val favoritesArray = root.optJSONArray("favorites")
            if (favoritesArray != null) {
                for (i in 0 until favoritesArray.length()) {
                    val obj = favoritesArray.getJSONObject(i)
                    favoritesList.add(
                        FavoriteEntity(
                            id = obj.optString("id", "fav_$i"),
                            name = obj.optString("name", "Canal $i"),
                            streamUrl = obj.optString("streamUrl", ""),
                            logoUrl = obj.optString("logoUrl", ""),
                            group = obj.optString("group", "General"),
                            epgId = obj.optString("epgId", "")
                        )
                    )
                }
            }

            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            Result.success(BackupPayload(profiles = profilesList, favorites = favoritesList, timestamp = timestamp))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
