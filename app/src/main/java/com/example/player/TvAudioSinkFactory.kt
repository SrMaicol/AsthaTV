package com.example.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Low-Level Audio HAL / AudioTrack Factory for Media3 ExoPlayer.
 * 1. Forces 16-bit PCM integer output (disables Float32 output).
 * 2. Uses device AudioCapabilities safely with standard platform decoders.
 * 3. Injects SonicAudioProcessor for sample rate stabilization.
 * 4. Adds AudioSink.Listener to catch low-level AudioTrack init errors.
 */
@OptIn(UnstableApi::class)
object TvAudioSinkFactory {

    private const val TAG = "TvAudioSinkFactory"

    fun createRenderersFactory(
        context: Context,
        onAudioSinkError: ((Exception) -> Unit)? = null
    ): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                val sonicAudioProcessor = SonicAudioProcessor()

                val audioProcessors: Array<AudioProcessor> = arrayOf(
                    sonicAudioProcessor
                )

                val audioCapabilities = try {
                    AudioCapabilities.getCapabilities(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get system AudioCapabilities, using default: ${e.message}")
                    AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
                }

                val audioSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false) // ❌ Desactivar Float32 (forzar PCM 16-bit entero)
                    .setAudioCapabilities(audioCapabilities)
                    .setAudioProcessors(audioProcessors)
                    .build()

                audioSink.setListener(object : AudioSink.Listener {
                    override fun onAudioSinkError(audioSinkError: Exception) {
                        Log.e(TAG, "AudioSink / AudioTrack error: ${audioSinkError.message}", audioSinkError)
                        onAudioSinkError?.invoke(audioSinkError)
                    }

                    override fun onPositionDiscontinuity() {}
                    override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                        Log.w(TAG, "AudioSink Underrun: bufferSize=$bufferSize, bufferSizeMs=$bufferSizeMs")
                    }
                    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {}
                })

                return audioSink
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
            setEnableAudioTrackPlaybackParams(true)
            setMediaCodecSelector(MediaCodecSelector.DEFAULT)
        }
    }
}
