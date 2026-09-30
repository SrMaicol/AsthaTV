package com.example.player

/**
 * Factory class responsible for instantiating video player engines.
 * Complies with the Factory Pattern and Open/Closed Principle.
 */
object PlayerFactory {

    /**
     * Creates and returns an implementation of CustomPlayer matching the requested engine.
     *
     * @param engine The desired engine (EXOPLAYER, LIB_VLC, IJKPLAYER).
     * @return An instance implementing CustomPlayer.
     */
    fun createPlayer(engine: PlayerEngineType = PlayerEngineType.EXOPLAYER): CustomPlayer {
        return when (engine) {
            PlayerEngineType.EXOPLAYER -> ExoPlayerImpl()
            PlayerEngineType.LIB_VLC -> VlcPlayerImpl()
            PlayerEngineType.NATIVE, PlayerEngineType.EXTERNAL_APP -> NativePlayerImpl()
        }
    }
}
