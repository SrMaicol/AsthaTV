package com.example.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Custom Cache & LoadControl Manager for Media3 ExoPlayer on Android TV.
 *
 * Features:
 * 1. 100MB Disk LRU Cache for IPTV segments & video streams to ensure zero stuttering & instant rewind.
 * 2. Optimized LoadControl with dynamic buffer tuning for Android TV devices.
 * 3. CacheDataSource wrapper with IGNORE_CACHE_ON_ERROR fallback for live streams.
 */
object PlayerCacheManager {

    @Volatile
    private var simpleCache: SimpleCache? = null

    private const val MAX_CACHE_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "exoplayer_tv_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }

    /**
     * Creates a CacheDataSource.Factory wrapping the upstream OkHttp DataSource Factory.
     */
    fun createCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        val cache = getCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Build an optimized Custom LoadControl tuned specifically for Android TV:
     * - Fast initial playback start (500ms initial buffer)
     * - Deep buffer for steady playback
     * - Prioritizes time over size thresholds to accommodate memory-constrained TV chipsets.
     */
    fun createCustomLoadControl(isLiveStream: Boolean = true): androidx.media3.exoplayer.DefaultLoadControl {
        return if (isLiveStream) {
            // Live IPTV Mode: Fast zapping + modest buffer
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    1500, // minBufferMs
                    8000, // maxBufferMs
                    500,  // bufferForPlaybackMs
                    1000  // bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(10000, true) // 10s back-buffer
                .build()
        } else {
            // VOD / Movie Mode: Deep buffer + seamless caching
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000, // minBufferMs: 15 seconds
                    50000, // maxBufferMs: 50 seconds
                    1000,  // bufferForPlaybackMs: 1s
                    2000   // bufferForPlaybackAfterRebufferMs: 2s
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(30000, true) // 30s back-buffer
                .build()
        }
    }
}
