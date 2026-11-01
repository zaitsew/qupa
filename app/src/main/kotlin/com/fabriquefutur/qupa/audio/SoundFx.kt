package com.fabriquefutur.qupa.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.fabriquefutur.qupa.sound.Effect
import com.fabriquefutur.qupa.sound.Synth
import java.util.concurrent.Executors

/**
 * Plays the synthesized effects. Samples are rendered once; each play makes a
 * short static AudioTrack on a single worker thread, so the UI never waits.
 */
class SoundFx {
    @Volatile
    var enabled: Boolean = true

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "qupa-sound").apply { isDaemon = true } }
    private val samples: Map<Effect, ShortArray> by lazy { Effect.entries.associateWith { Synth.render(it) } }

    fun play(effect: Effect) {
        if (!enabled) return
        worker.execute {
            val pcm = samples.getValue(effect)
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Synth.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(Synth.durationMs(effect) + 60L)
            } catch (_: Exception) {
                // No audio device or a busy one: the game goes on without the effect.
            } finally {
                runCatching { track?.release() }
            }
        }
    }

    fun release() {
        worker.shutdown()
    }
}
