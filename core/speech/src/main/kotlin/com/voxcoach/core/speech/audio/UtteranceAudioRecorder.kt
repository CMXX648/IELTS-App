package com.voxcoach.core.speech.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.voxcoach.core.domain.model.MimoDefaults
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

/**
 * Short-lived PCM16 capture for press-to-talk when no session tap is available.
 */
internal class UtteranceAudioRecorder(
    private val sampleRate: Int = MimoDefaults.ASR_SAMPLE_RATE,
) {
    private val lock = Any()
    private val pcm = ByteArrayOutputStream()
    @Volatile private var recording = false
    private var record: AudioRecord? = null
    private var reader: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        synchronized(lock) {
            stopInternal(discard = true)
            val min = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (min <= 0) {
                throw IllegalStateException("AudioRecord buffer unavailable")
            }
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                min * 2,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                throw IllegalStateException("AudioRecord init failed")
            }
            pcm.reset()
            recording = true
            record = rec
            rec.startRecording()
            reader = thread(name = "vox-utterance-asr", isDaemon = true) {
                val buf = ByteArray(min)
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        synchronized(pcm) { pcm.write(buf, 0, n) }
                    }
                }
            }
        }
    }

    fun stopToWav(): ByteArray {
        synchronized(lock) {
            return stopInternal(discard = false)
        }
    }

    private fun stopInternal(discard: Boolean): ByteArray {
        recording = false
        reader?.join(800)
        reader = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        val raw = synchronized(pcm) { pcm.toByteArray() }
        pcm.reset()
        if (discard || raw.isEmpty()) return ByteArray(0)
        return WavEncoder.pcm16LeMonoToWav(raw, sampleRate)
    }
}
