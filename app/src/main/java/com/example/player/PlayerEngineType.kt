package com.example.player

/**
 * Supported video player engines.
 */
enum class PlayerEngineType(
    val title: String,
    val shortName: String,
    val subtitle: String,
    val tag: String
) {
    EXOPLAYER(
        title = "ExoPlayer (Google Media3)",
        shortName = "ExoPlayer",
        subtitle = "Google Media3 • Aceleración hardware, bajo consumo y baja latencia",
        tag = "Media3"
    ),
    LIB_VLC(
        title = "LibVLC (VideoLAN SDK)",
        shortName = "LibVLC",
        subtitle = "VideoLAN SDK • Máxima compatibilidad con codecs de video y audio",
        tag = "VLC SDK"
    ),
    NATIVE(
        title = "Reproductor Nativo (Android OS)",
        shortName = "Nativo",
        subtitle = "Decodificador nativo del sistema (MediaPlayer / VideoView)",
        tag = "Sistema"
    ),
    EXTERNAL_APP(
        title = "Reproductor Externo (Cualquier App)",
        shortName = "App Externa",
        subtitle = "Abrir con VLC, MX Player, Nova, Kodi o cualquier reproductor de la TV",
        tag = "Externo"
    )
}
