package app.netoverdose.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Short UI tones synthesised on the fly, in the spirit of the reference prototype's Web Audio
 * beeps but with its own frequencies and envelopes. No audio assets ship with the app.
 *
 * Each sound is rendered off the main thread and played through a throw-away AudioTrack, so
 * overlapping taps never block the UI.
 */
class Sounds(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("netoverdose_settings", Context.MODE_PRIVATE)

    /** Persisted mute switch, exposed in the Lab. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Light click for taps, chips and toggles. */
    fun tick() = play(listOf(Tone(880.0, 0.030, 0.10)))

    /** Duller thud for destructive or dismissive actions. */
    fun thunk() = play(listOf(Tone(240.0, 0.075, 0.16)))

    /** Two rising notes for successful operations. */
    fun confirm() = play(listOf(Tone(620.0, 0.070, 0.12), Tone(930.0, 0.110, 0.11)))

    /** Closing a task: a short three-note phrase. */
    fun complete() = play(
        listOf(Tone(560.0, 0.070, 0.11), Tone(740.0, 0.070, 0.11), Tone(990.0, 0.150, 0.10))
    )

    /** Sprint finished: quick ascending run. */
    fun sprint() = play(
        listOf(Tone(500.0, 0.060, 0.10), Tone(700.0, 0.060, 0.10), Tone(900.0, 0.120, 0.10))
    )

    private fun play(tones: List<Tone>) {
        if (!enabled) return
        scope.launch { runCatching { render(tones) } }
    }

    private fun render(tones: List<Tone>) {
        val sampleRate = 44_100
        val total = tones.sumOf { (it.durationSeconds * sampleRate).toInt() } + sampleRate / 100
        val buffer = ShortArray(total)
        var cursor = 0
        for (tone in tones) {
            val count = (tone.durationSeconds * sampleRate).toInt()
            for (i in 0 until count) {
                if (cursor + i >= buffer.size) break
                val t = i.toDouble() / sampleRate
                // Fast attack and exponential decay keep the edges click-free.
                val attack = (t / 0.004).coerceAtMost(1.0)
                val decay = exp(-t / (tone.durationSeconds * 0.45))
                val sample = sin(2.0 * PI * tone.frequency * t) * attack * decay * tone.gain
                buffer[cursor + i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
            cursor += count
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.setVolume(volume())
        track.play()

        val lifetime = buffer.size * 1000L / sampleRate + 120L
        scope.launch {
            delay(lifetime)
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    private fun volume(): Float {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val current = manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val max = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
        if (max <= 0) return 0.5f
        // Sonification should sit under everything else, never at full scale.
        return (0.25f + 0.5f * current.toFloat() / max).coerceIn(0.25f, 0.75f)
    }

    private data class Tone(val frequency: Double, val durationSeconds: Double, val gain: Double)

    private companion object {
        const val KEY_ENABLED = "sound_enabled"
    }
}
