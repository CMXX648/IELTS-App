package com.voxcoach.core.speech.recording

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.voxcoach.core.domain.model.MimoDefaults
import com.voxcoach.core.domain.speech.SessionAudioCapture
import com.voxcoach.core.speech.audio.WavEncoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * Continuous PCM16 capture → local .wav (RP-01). Also exposes in-memory slices
 * so MiMo ASR can reuse the same microphone session.
 */
@Singleton
class MediaRecorderSessionAudioCapture @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SessionAudioCapture {

    private val lock = Any()
    private val pcmLock = Any()
    private val pcm = ByteArrayOutputStream()
    private var record: AudioRecord? = null
    private var reader: Thread? = null
    private var outputPath: String? = null
    private var startedAtElapsed: Long = 0L
    @Volatile private var recording: Boolean = false

    override val isRecording: Boolean
        get() = recording

    @SuppressLint("MissingPermission")
    override fun start(sessionId: String): String {
        synchronized(lock) {
            if (recording) {
                stopInternal(keepPath = true, writeFile = true)
            }
            val dir = File(context.filesDir, RECORDINGS_DIR).apply { mkdirs() }
            val file = File(dir, "$sessionId.wav")
            val path = file.absolutePath
            val min = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (min <= 0) {
                throw IllegalStateException("AudioRecord buffer unavailable")
            }
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                min * 2,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                throw IllegalStateException("AudioRecord init failed")
            }
            synchronized(pcmLock) { pcm.reset() }
            recording = true
            record = rec
            rec.startRecording()
            reader = thread(name = "vox-session-pcm", isDaemon = true) {
                val buf = ByteArray(min)
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        synchronized(pcmLock) { pcm.write(buf, 0, n) }
                    }
                }
            }
            outputPath = path
            startedAtElapsed = SystemClock.elapsedRealtime()
            startForegroundService(sessionId)
            return path
        }
    }

    override fun elapsedMs(): Long {
        if (!recording) return 0L
        return SystemClock.elapsedRealtime() - startedAtElapsed
    }

    override fun stop(): String? = synchronized(lock) {
        stopInternal(keepPath = true, writeFile = true)
    }

    override fun release() {
        synchronized(lock) {
            stopInternal(keepPath = true, writeFile = true)
        }
    }

    override fun pcmByteCursor(): Int? {
        if (!recording) return null
        return synchronized(pcmLock) { pcm.size() }
    }

    override fun pcmSliceToWav(fromByte: Int): ByteArray? {
        if (!recording && pcm.size() == 0) return null
        val raw = synchronized(pcmLock) { pcm.toByteArray() }
        val from = fromByte.coerceIn(0, raw.size)
        val slice = raw.copyOfRange(from, raw.size)
        if (slice.isEmpty()) return ByteArray(0)
        return WavEncoder.pcm16LeMonoToWav(slice, SAMPLE_RATE)
    }

    private fun stopInternal(keepPath: Boolean, writeFile: Boolean): String? {
        val path = outputPath
        recording = false
        reader?.join(800)
        reader = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        stopForegroundService()
        if (writeFile && path != null) {
            val raw = synchronized(pcmLock) { pcm.toByteArray() }
            runCatching {
                File(path).writeBytes(WavEncoder.pcm16LeMonoToWav(raw, SAMPLE_RATE))
            }.onFailure { Log.w(TAG, "write wav", it) }
        }
        synchronized(pcmLock) { pcm.reset() }
        if (!keepPath) outputPath = null
        return path
    }

    private fun startForegroundService(sessionId: String) {
        val intent = Intent(context, SessionRecordingService::class.java).apply {
            action = SessionRecordingService.ACTION_START
            putExtra(SessionRecordingService.EXTRA_SESSION_ID, sessionId)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { Log.w(TAG, "start FGS failed", it) }
    }

    private fun stopForegroundService() {
        val intent = Intent(context, SessionRecordingService::class.java).apply {
            action = SessionRecordingService.ACTION_STOP
        }
        runCatching { context.startService(intent) }
            .onFailure { Log.w(TAG, "stop FGS failed", it) }
    }

    companion object {
        private const val TAG = "SessionAudioCapture"
        const val RECORDINGS_DIR = "recordings"
        private const val SAMPLE_RATE = MimoDefaults.ASR_SAMPLE_RATE
    }
}
