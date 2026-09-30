package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IptvApplication : Application(), ImageLoaderFactory {

    private val coilOkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(4, 3, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(coilOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.12) // Optimización agresiva para Android TV (1GB RAM)
                    .strongReferencesEnabled(false) // Permite GC suave sin congelar la UI
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("channel_logos_cache"))
                    .maxSizeBytes(40L * 1024 * 1024) // 40MB en almacenamiento para respuesta instantánea
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565) // 50% ahorro de RAM en bitmaps
            .allowHardware(false) // Evita conflictos de AHardwareBuffer / eglQueryContext en emuladores y GPUs TV
            .crossfade(false) // Desactiva animaciones de fundido para cero carga GPU en TVs básicas
            .respectCacheHeaders(false) // Caché perenne de logos
            .build()
    }
}
