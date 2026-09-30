package com.example.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton holder for heavy player resources to avoid resource exhaustion on Android TV.
 */
object PlayerResourceHolder {

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    @Synchronized
    fun getOkHttpClient(): OkHttpClient {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
        return okHttpClient!!
    }

    fun createDataSourceFactory(): DataSource.Factory {
        return OkHttpDataSource.Factory(getOkHttpClient())
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) IPTV/3.0")
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Icy-MetaData" to "1",
                    "Connection" to "keep-alive"
                )
            )
    }

    val hlsExtractorFactory by lazy {
        DefaultHlsExtractorFactory(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
            true
        )
    }
}
