package com.voxcoach.core.speech.recording

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class RecordingPathTest {
    @Test
    fun sessionAudioFileUsesWavUnderRecordingsDir() {
        val sessionId = "sess-abc"
        val dir = File("/tmp", MediaRecorderSessionAudioCapture.RECORDINGS_DIR)
        val file = File(dir, "$sessionId.wav")
        assertThat(file.name).isEqualTo("sess-abc.wav")
        assertThat(file.extension).isEqualTo("wav")
        assertThat(file.parentFile!!.name).isEqualTo("recordings")
    }
}
