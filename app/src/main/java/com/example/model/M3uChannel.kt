package com.example.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

/**
 * LiveChannel representation extracted from M3U playlist.
 */
@Immutable
data class LiveChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val group: String = "General",
    val epgId: String = ""
) : Serializable

// Typealias for full compatibility with existing components
typealias M3uChannel = LiveChannel
