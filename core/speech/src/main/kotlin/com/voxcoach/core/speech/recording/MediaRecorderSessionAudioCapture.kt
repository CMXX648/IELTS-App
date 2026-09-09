package com.voxcoach.core.speech.recording

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.voxcoach.core.domain.speech.SessionAudioCapture
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaRecorder → AAC/MPEG-4 (.m4a). Holds a keep-alive FGS while recording.
 * Never uploads; files under app-private `files/recordings/`.
 */
@Singleton
class MediaRecorderSessionAudioCapture @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SessionAudioCapture {

    private val lock = Any()
    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null
    private var startedAtElapsed: Long = 0L
    @Volatile private var recording: Boolean = false

    override val isRecording: Boolean
        get() = recording

    override fun start(sessionId: String): String {
        synchronized(lock) {
            if (recording) {
                stopInternal(keepPath = true)
            }
            val dir = File(context.filesDir, RECORDINGS_DIR).apply { mkdirs() }
            val file = File(dir, "$sessionId.m4a")
            val path = file.absolutePath
            val mr = createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(path)
                prepare()
                start()
            }
            recorder = mr
            outputPath = path
            startedAtElapsed = android.os.SystemClock.elapsedRealtime()
            recording = true
            startForegroundService(sessionId)
            return path
        }
    }

    override fun elapsedMs(): Long {
        if (!recording) return 0L
        return android.os.SystemClock.elapsedRealtime() - startedAtElapsed
    }

    override fun stop(): String? = synchronized(lock) {
        stopInternal(keepPath = true)
    }

    override fun release() {
        synchronized(lock) {
            stopInternal(keepPath = true)
        }
    }

    private fun stopInternal(keepPath: Boolean): String? {
        val path = outputPath
        try {
            recorder?.run {
                runCatching {
                    if (recording) stop()
                }
                runCatching { reset() }
                runCatching { release() }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stop/release recorder", t)
        } finally {
            recorder = null
            recording = false
            stopForegroundService()
            if (!keepPath) outputPath = null
        }
        return path
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
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
    }
}
